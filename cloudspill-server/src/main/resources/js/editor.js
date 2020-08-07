function edit() {
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

    let currentTag = "";
    let suffix = "";

    function placeCursor() {
        if (cursorPos === tagElts.length) {
            container.appendChild(cursorElt);
        } else {
            container.insertBefore(cursorElt, tagElts[cursorPos]);
        }
    }

    function createTag() {
        let value = (currentTag + suffix).trim();
        if (value.length > 0) {
            stubElt.textContent = value;
            stubElt.className = 'tag';
            tagElts.splice(cursorPos, 0, stubElt);
            cursorPos++;
        } else if (stubElt) {
            container.removeChild(stubElt);
        }
        container.removeChild(suffixElt);

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
                suffixElt = document.createElement("span");
                container.insertBefore(stubElt, cursorElt);
                container.insertBefore(suffixElt, cursorElt.nextSibling);
            }
            stubElt.textContent = currentTag;
            suffixElt.textContent = suffix;
        } else {
            if (stubElt) {
                container.removeChild(stubElt);
                container.removeChild(suffixElt);
                stubElt = null;
                suffixElt = null;
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
        } else if (event.key === "Enter" || event.key === ",") {
            createTag();
        } else if (event.key.length === 1) {
            currentTag += event.key;
            updateStub();
        } else {
            console.log("Unhandled key code ", event.key);
        }
    };
}