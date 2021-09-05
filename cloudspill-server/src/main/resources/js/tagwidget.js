/** Convert an existing tag list into an editable tag list.
 * tagsElt: the component containing existing <... class='tag'> tags.
 * submitTag: function taking a tag change specification to save to server.
 * returns: a function to remove edition widget */
function createTagWidget(tagsElt, knownTagUrl, submitTag) {
    if ([...tagsElt.children].some(t => t.classList.contains('stub'))) {
        return; // tags are already editable
    }

    /* "tag" Elements */
    let tagElts = [...tagsElt.children].filter(t => t.classList.contains('tag'));
    /* Position of the stub element in the tag array. 0 means before first element, n means after nth element. */
    let cursorPos = tagElts.length;
    /* A pop-up shown below the stub, when completions exist. The holder is needed for a CSS hack, and contains the actual pop-up. */
    let popupHolderElt = null;
    let popupElt = null;
    let selectedOption = null;

    /* Set to an empty array if a tag submission query is running. Additional submissions should be added to the array rather
     * than sending a parallel query to avoid race conditions. When a query returns, whatever is in the array is sent in a follow-up query. */
    let tagSubmissionQueue = null;

    let knownTags = [];
    let knownTagReq = new XMLHttpRequest();
    knownTagReq.onreadystatechange = () => {
        if (knownTagReq.readyState != 4) return;
        knownTags = JSON.parse(knownTagReq.responseText);
    };
    knownTagReq.open("GET", knownTagUrl);
    knownTagReq.send();

    let stubElt = document.createElement('span');
    stubElt.setAttribute('contenteditable', 'true');
    stubElt.className = 'stub';
    stubElt.addEventListener('input', event => {
        if (hasStub()) {
            let searchString = stubElt.textContent;
            let filteredTags = knownTags.filter(elt => elt === searchString);
            Array.prototype.push.apply(filteredTags, knownTags.filter(elt => elt.startsWith(searchString) && elt !== searchString));
            // could do more subtle things by looking at selection. For instance see how we can add things at the cursor AND keep the text after cursor
            //Array.prototype.push.apply(filteredTags, knownTags.filter(elt => elt.includes(currentTag + suffix) && !elt.startsWith(searchString)));
            if (filteredTags.length === 0) {
                removePopup();
            } else {
                if (!popupHolderElt) {
                    popupHolderElt = document.createElement('div');
                    popupHolderElt.className = 'pop-up-holder';
                    popupElt = document.createElement('div');
                    popupElt.className = 'pop-up';
                    popupHolderElt.appendChild(popupElt);
                    tagsElt.insertBefore(popupHolderElt, stubElt);
                }
                console.log(filteredTags);
                popupElt.innerHTML='';
                filteredTags.forEach(tag => {
                    let option = document.createElement('div');
                    option.dataset.tag = tag;
                    option.textContent = tag; // TODO highlight search string
                    option.addEventListener('click', event => {
                        createTag(tag);
                    });
                    popupElt.appendChild(option);
                });
                selectedOption = popupElt.firstChild;
                selectedOption.className = 'selected-option';
            }
        }
    });
    stubElt.addEventListener('blur', e => removePopup()); // TODO UNLESS we just clicked on the popup
    stubElt.onkeydown = event => {
        // (Trigger events when shift is pressed)
        if (!event.altKey && !event.ctrlKey && !event.metaKey) {
            if (event.key === "ArrowLeft") {
                if (cursorPos > 0 && !hasStub()) {
                    cursorPos--;
                    placeCursor();
                    event.preventDefault();
                }
            } else if (event.key === "ArrowRight") {
                if (cursorPos < tagElts.length && !hasStub()) {
                    cursorPos++;
                    placeCursor();
                    event.preventDefault();
                }
            } else if (event.key === "ArrowUp") {
                if (selectedOption && selectedOption.previousSibling) {
                    selectedOption.className = '';
                    selectedOption = selectedOption.previousSibling;
                    selectedOption.className = 'selected-option';
                    event.preventDefault();
                }
            } else if (event.key === "ArrowDown") {
                if (selectedOption && selectedOption.nextSibling) {
                    selectedOption.className = '';
                    selectedOption = selectedOption.nextSibling;
                    selectedOption.className = 'selected-option';
                    event.preventDefault();
                }
            } else if (event.key === "Backspace") {
                if (cursorPos > 0 && !hasStub()) {
                    /* Remove previous tag */
                    let tagElt = tagElts[cursorPos - 1];
                    queueTag(['-'+ tagElt.dataset.tag]);
                    tagsElt.removeChild(tagElt);
                    tagElts.splice(cursorPos - 1, 1);
                    cursorPos--;
                    event.preventDefault();
                }
            } else if (event.key === "Delete") {
                if (cursorPos < tagElts.length && !hasStub()) {
                    /* Remove next tag */
                    let tagElt = tagElts[cursorPos];
                    queueTag(['-'+ tagElt.dataset.tag]);
                    tagsElt.removeChild(tagElt);
                    tagElts.splice(cursorPos, 1);
                    event.preventDefault();
                }
            } else if (event.key === "Enter") {
                if (hasStub()) {
                    createTag(selectedOption && selectedOption.dataset.tag || stubElt.textContent.trim());
                    event.preventDefault();
                }
            } else if (event.key === "Escape") {
                if (popupHolderElt) {
                    removePopup();
                    event.preventDefault();
                }
            } else if (event.key === ",") {
                if (!hasStub()) {
                    return;
                }
                let selection = document.getSelection();
                if (selection.anchorNode.parentElement !== stubElt) {
                    return;
                }
                // pretend we replaced the selection with a tag-separator.
                // so create a tag from start to the nearest selection, delete the selection, and keep the other end of the selection until the end of the string.
                createTag(
                    stubElt.textContent.substring(0, Math.min(selection.anchorOffset, selection.focusOffset)).trim(),
                    stubElt.textContent.substring(Math.max(selection.anchorOffset, selection.focusOffset)).trim());
                event.preventDefault();
            }
        } // else: do nothing when ctrl/alt/meta is pressed
    };

    function queueTag(spec /*: string[]*/ ) {
        if (tagSubmissionQueue !== null) {
            spec.forEach(newTag => {
                /* Cancel contradicting tags x and -x */
                let negation = newTag.startsWith('-') ? newTag.substring(1) : ('-'+ newTag);
                let negationIndex = tagSubmissionQueue.indexOf(negation);
                if (negation !== -1) {
                    tagSubmissionQueue.splice(negationIndex, 1);
                } else {
                    tagSubmissionQueue.push(newTag);
                }
            });
            Array.prototype.push.apply(tagSubmissionQueue, spec);
            return;
        }

        tagSubmissionQueue = [];
        tagsElt.classList.add('busy');

        submitTag(spec.join(), () => {
            let followUp = tagSubmissionQueue;
            tagSubmissionQueue = null;
            tagsElt.classList.remove('busy');
            if (followUp.length > 0) {
                queueTag(followUp);
            }
        });
    }

    function placeCursor() {
        console.log("cursorPos", cursorPos);
        if (cursorPos === tagElts.length) {
            tagsElt.appendChild(stubElt);
        } else {
            tagsElt.insertBefore(stubElt, tagElts[cursorPos]);
        }
        stubElt.focus();
    }

    function removePopup() {
        if (popupHolderElt) {
            tagsElt.removeChild(popupHolderElt);
        }
        popupElt = null;
        popupHolderElt = null;
        selectedOption = null;
    }

    function createTag(value, remainder) {
        /* Don't allow tags to start with '-' because that means deleting a tag */
        while (value.trim().startsWith('-')) {
            // note: this remove initial spaces
            value = value.substring(1);
        }
        let tagSpec = [];
        if (value.length > 0) {
            let newTag = document.createElement('span');
            newTag.className = 'tag';
            newTag.textContent = value;
            newTag.dataset.tag = value;
            tagElts.splice(cursorPos, 0, newTag);
            tagsElt.insertBefore(newTag, stubElt);
            cursorPos++;
            tagSpec.push(value);
        }

        removePopup();

        stubElt.textContent = remainder || '';
        placeCursor();

        if (tagSpec.length > 0) {
            queueTag(tagSpec);
        }
    }

    function hasStub() {
        return stubElt.textContent.length;
    }

    function stopEditTags() {
        removePopup();
        if (stubElt) {
            tagsElt.removeChild(stubElt);
        }
        stubElt = null;
    }
    tagsElt.onclick = event => {
        cursorPos = tagElts.length;
        placeCursor();
        event.preventDefault();
    };

    placeCursor();

    return () => stopEditTags();
}