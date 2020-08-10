function edit(knownTagUrl) {
    /* Hide "EDIT" button */
    document.getElementById('edit').style.display='none';
    /* "tags" Element */
    let container = document.getElementsByClassName("tags")[0];
    /* "tag" Elements */
    let tagElts = [...document.getElementsByClassName("tag")];
    let cursorElt = document.createElement("span");
    cursorElt.setAttribute("class", "cursor");
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

    let knownTags = [];
    let knownTagReq = new XMLHttpRequest();
    knownTagReq.onreadystatechange = () => {
        if (knownTagReq.readyState != 4) return;
        knownTags = JSON.parse(knownTagReq.responseText);
    };
    knownTagReq.open("GET", knownTagUrl);
    knownTagReq.send();

    function placeCursor() {
        if (cursorPos === tagElts.length) {
            container.appendChild(cursorElt);
        } else {
            container.insertBefore(cursorElt, tagElts[cursorPos]);
        }
    }

    function createTag(first, second) {
        if (first.length > 0) {
            stubElt.textContent = first;
            stubElt.className = 'tag';
            tagElts.splice(cursorPos, 0, stubElt);
            cursorPos++;
        } else if (stubElt) {
            /* User typed only spaces */
            container.removeChild(stubElt);
        }
        if (second && second.length > 0) {
            suffixElt.textContent = second;
            suffixElt.className = 'tag';
            tagElts.splice(cursorPos, 0, suffixElt);
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
    }

    function hasStub() {
        return (currentTag.length > 0 || suffix.length > 0);
    }

    function updateStub() {
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
                container.removeChild(stubElt);
                container.removeChild(suffixElt);
                if (popupHolderElt) {
                    container.removeChild(popupHolderElt);
                }
                stubElt = null;
                suffixElt = null;
                popupElt = null;
                popupHolderElt = null;
                selectedOption = null;
            }
        }
    }

    placeCursor();

    document.onkeydown = event => {
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
                container.removeChild(tagElts[cursorPos - 1]);
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
                container.removeChild(tagElts[cursorPos]);
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
        }
    };
}