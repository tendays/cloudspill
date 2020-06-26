function edit() {
    /* "tags" Element */
    var container = document.getElementsByClassName("tags")[0];
    /* "tag" Elements */
    var tagElts = [...document.getElementsByClassName("tag")];
    var cursorElt = document.createElement("span");
    cursorElt.setAttribute("class", "cursor");
    var cursorPos = tagElts.length;
    var stubElt = null;
    var currentTag = "";

    function placeCursor() {
        if (cursorPos === tagElts.length) {
            container.appendChild(cursorElt);
        } else {
            container.insertBefore(cursorElt, tagElts[cursorPos]);
        }
    }

    placeCursor();

    document.onkeydown = event => {
        if (event.key === "ArrowLeft") {
            if (cursorPos > 0) { cursorPos--; }
            placeCursor();
        } else if (event.key === "ArrowRight") {
            if (cursorPos < tagElts.length) { cursorPos++;}
            placeCursor();
        } else if (event.key.length === 1) {
            if (!stubElt) {
                stubElt = document.createElement("span");
                container.insertBefore(stubElt, cursorElt);
            }
            currentTag += event.key;
            stubElt.innerHTML = currentTag;
        }
    };
}