function edit(itemId, knownTagUrl, submitTagUrl) {
    /* Hide "EDIT" button */
    document.getElementById('edit').style.display='none';
    /* "tags" Element */
    let container = document.getElementsByClassName("tags")[0];

    let descriptionElt = document.getElementById('description');
    descriptionElt.setAttribute('contenteditable', 'true');
    descriptionElt.classList.add('description-editor');
    let descriptionDirty = false;
    descriptionElt.oninput = event => {
        descriptionDirty = true;
    };
    descriptionElt.onblur = event => {
        if (descriptionDirty) {
            descriptionElt.classList.add('busy');
            saveDescription(itemId, descriptionElt.textContent, () => {
                descriptionElt.classList.remove('busy');
            });
        }

        descriptionElt.textContent = descriptionElt.textContent;
        descriptionDirty = false;
    };

    createTagWidget(container, knownTagUrl, (spec /*: string*/, callback) => {
        let req = new XMLHttpRequest();
        req.onreadystatechange = () => {
            if (req.readyState === 4) {
                callback();
            }
        };
        req.open("PUT", submitTagUrl);
        req.send(spec);
    });

    event.preventDefault();
}