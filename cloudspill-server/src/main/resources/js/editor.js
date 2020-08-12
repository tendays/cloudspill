function edit(knownTagUrl, submitTagUrl) {
    /* Hide "EDIT" button */
    document.getElementById('edit').style.display='none';
    let descriptionElt = document.getElementById('description');
    descriptionElt.setAttribute('contenteditable', 'true');
    /* "tags" Element */
    let container = document.getElementsByClassName("tags")[0];
    /* "tag" Elements */
    let tagElts = [...document.getElementsByClassName("tag")];
    let cursorElt = null;
    /* Position of the cursor (and stub element, if applicable) in the tag array. 0 means before first element, n means after nth element. */
    let cursorPos = tagElts.length;
    /* Tag being created. */
    let stubElt = null;
    /* When navigating the cursor within the stub, this is a <span> of the text after the cursor */
    let suffixElt = null;
    /* A pop-up shown below the stub, when completions exist. The holder is needed for a CSS hack, and contains the actual pop-up. */
    let popupHolderElt = null;
    let popupElt = null;
    let selectedOption = null;

    let currentTag = "";
    let suffix = "";
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

    function submitTag(spec /*: string[]*/ ) {
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
        container.classList.add('busy');
        let req = new XMLHttpRequest();
        req.onreadystatechange = () => {
            if (req.readyState != 4) return;
            let followUp = tagSubmissionQueue;
            tagSubmissionQueue = null;
            container.classList.remove('busy');
            if (followUp.length > 0) {
                submitTag(followUp);
            }
        };
        req.open("PUT", submitTagUrl);
        req.send(spec.join());
    }

    function placeCursor() {
        if (cursorPos === tagElts.length) {
            container.appendChild(cursorElt);
        } else {
            container.insertBefore(cursorElt, tagElts[cursorPos]);
        }
    }

    function createTag(first, second) {
        let tagSpec = [];
        if (first.length > 0) {
            stubElt.textContent = first;
            stubElt.className = 'tag';
            stubElt.dataset.tag = first;
            tagElts.splice(cursorPos, 0, stubElt);
            cursorPos++;
            tagSpec.push(first);
        } else if (stubElt) {
            /* User typed only spaces */
            container.removeChild(stubElt);
        }
        if (second && second.length > 0) {
            suffixElt.textContent = second;
            suffixElt.dataset.tag = second;
            suffixElt.className = 'tag';
            tagElts.splice(cursorPos, 0, suffixElt);
            tagSpec.push(second);
        } else {
            container.removeChild(suffixElt);
        }

        if (popupHolderElt) {
            container.removeChild(popupHolderElt);
        }
        popupElt = null;
        popupHolderElt = null;

        currentTag = '';
        suffix = '';

        stubElt = null;
        suffixElt = null;

        placeCursor();

        if (tagSpec.length > 0) {
            submitTag(tagSpec);
        }
    }

    function hasStub() {
        return (currentTag.length > 0 || suffix.length > 0);
    }

    function clearStub() {
        if (stubElt) {
            container.removeChild(stubElt);
            container.removeChild(suffixElt);
        }
        if (popupHolderElt) {
            container.removeChild(popupHolderElt);
        }
        currentTag = "";
        suffix = "";
        stubElt = null;
        suffixElt = null;
        popupElt = null;
        popupHolderElt = null;
        selectedOption = null;
    }

    function updateStub() {
        /* Don't allow tags to start with '-' because that means deleting a tag */
        while (currentTag.trim().startsWith('-')) {
            // note: this remove initial spaces
            currentTag = currentTag.substring(1);
        }
        while ((currentTag + suffix).trim().startsWith('-')) {
            suffix = suffix.substring(1);
        }

        if (hasStub()) {
            if (!stubElt) {
                stubElt = document.createElement("span");
                stubElt.className = 'stub';
                suffixElt = document.createElement("span");
                container.insertBefore(stubElt, cursorElt);
                container.insertBefore(suffixElt, cursorElt.nextSibling);
            }
            stubElt.textContent = currentTag;
            suffixElt.textContent = suffix;

            let searchString = currentTag + suffix;
            let filteredTags = knownTags.filter(elt => elt === searchString);
            Array.prototype.push.apply(filteredTags, knownTags.filter(elt => elt.startsWith(searchString) && elt !== searchString));
            Array.prototype.push.apply(filteredTags, knownTags.filter(elt => elt.includes(currentTag + suffix) && !elt.startsWith(searchString)));
            if (filteredTags.length === 0) {
                if (popupHolderElt) {
                    container.removeChild(popupHolderElt);
                    popupHolderElt = null;
                    popupElt = null;
                    selectedOption = null;
                }
            } else {
                if (!popupHolderElt) {
                    popupHolderElt = document.createElement('div');
                    popupHolderElt.className = 'pop-up-holder';
                    popupElt = document.createElement('div');
                    popupElt.className = 'pop-up';
                    popupHolderElt.appendChild(popupElt);
                    container.insertBefore(popupHolderElt, stubElt);
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
        } else {
            if (stubElt) {
                clearStub();
            }
        }
    }

    function stopEditTags() {
        if (cursorElt) {
            clearStub();
            document.onkeydown = null;
            container.removeChild(cursorElt);
            cursorElt = null;
        }
    }

    function editTags() {
        if (cursorElt) { return; }

        cursorElt = document.createElement("span");
        cursorElt.setAttribute("class", "cursor");
        placeCursor();
        descriptionElt.onfocus = event => {
            stopEditTags();
        };
        descriptionElt.onblur = event => {
            console.log('new description', descriptionElt.textContent);
        };
        document.onkeydown = event => {
            // (Trigger events when shift is pressed)
            if (!event.altKey && !event.ctrlKey && !event.metaKey) {
                if (event.key === "ArrowLeft") {
                    if (hasStub()) {
                        if (currentTag.length > 0) {
                            suffix = currentTag.substring(currentTag.length - 1) + suffix;
                            currentTag = currentTag.substring(0, currentTag.length - 1);
                            updateStub();
                        }
                    } else if (cursorPos > 0) {
                        cursorPos--;
                        placeCursor();
                    }
                } else if (event.key === "ArrowRight") {
                    if (hasStub()) {
                        if (suffix.length > 0) {
                            currentTag = currentTag + suffix.substring(0, 1);
                            suffix = suffix.substring(1);
                            updateStub();
                        }
                    } else if (cursorPos < tagElts.length) {
                        cursorPos++;
                        placeCursor();
                    }
                } else if (event.key === "ArrowUp") {
                    if (selectedOption && selectedOption.previousSibling) {
                        selectedOption.className = '';
                        selectedOption = selectedOption.previousSibling;
                        selectedOption.className = 'selected-option';
                    }
                } else if (event.key === "ArrowDown") {
                    if (selectedOption && selectedOption.nextSibling) {
                        selectedOption.className = '';
                        selectedOption = selectedOption.nextSibling;
                        selectedOption.className = 'selected-option';
                    }
                } else if (event.key === "Backspace") {
                    if (hasStub()) {
                        if (currentTag.length > 0) {
                            currentTag = currentTag.substring(0, currentTag.length - 1);
                            updateStub();
                        }
                    } else if (cursorPos > 0) {
                        /* Remove previous tag */
                        let tagElt = tagElts[cursorPos - 1];
                        submitTag(['-'+ tagElt.dataset.tag]);
                        container.removeChild(tagElt);
                        tagElts.splice(cursorPos - 1, 1);
                        cursorPos--;
                    }
                } else if (event.key === "Delete") {
                    if (hasStub()) {
                        if (suffix.length > 0) {
                            suffix = suffix.substring(1);
                            updateStub();
                        }
                    } else if (cursorPos < tagElts.length) {
                        /* Remove next tag */
                        let tagElt = tagElts[cursorPos];
                        submitTag(['-'+ tagElt.dataset.tag]);
                        container.removeChild(tagElt);
                        tagElts.splice(cursorPos, 1);
                    }
                } else if (event.key === "Enter") {
                    if (hasStub()) {
                        createTag(selectedOption && selectedOption.dataset.tag || (currentTag + suffix).trim());
                    }
                } else if (event.key === "Escape") {
                    if (popupHolderElt) {
                        container.removeChild(popupHolderElt);
                        popupHolderElt = null;
                        popupElt = null;
                        selectedOption = null;
                    } else {
                        stopEditTags();
                    }
                } else if (event.key === ",") {
                    if (hasStub()) {
                        createTag(currentTag.trim(), suffix.trim());
                    }
                } else if (event.key.length === 1) {
                    currentTag += event.key;
                    updateStub();
                } else {
                    console.log("Unhandled key code ", event.key);
                    return; // keep default behaviour for non-handled keys
                }
                event.preventDefault();
            } // else: do nothing when ctrl/alt/meta is pressed
        };
    }
    editTags();
    container.onclick = event => {
        editTags();
    };
}