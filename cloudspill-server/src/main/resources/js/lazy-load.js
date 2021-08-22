function createPlaceholders(dataUrl, imageUrlPattern, hrefPattern, pageSize, count) {
    let placeholders = {};

    let marker = document.getElementById("marker");
    let pointer = marker; // the current insertion position

    for (let offset = pageSize; offset < count; offset += pageSize) {
        let observer = new IntersectionObserver((entries, observer) => {
            if (entries.some(entry => entry.intersectionRatio > 0)) {
                let req = new XMLHttpRequest();
                req.onreadystatechange = () => {
                    if (req.readyState != 4) return; // State 4 is DONE

                    let response = JSON.parse(req.responseText);
                    let data = response.data;
                    for (let i=0; i<data.length; i++) {
                        placeholders[i+offset].href = hrefPattern.replace("%d", data[i].id).replace("%s", data[i].checksum)
                        placeholders[i+offset].innerHTML =
                            "<img class='thumb', src='" +
                            imageUrlPattern.replace("%d", data[i].id).replace("%s", data[i].checksum) +"'>";
                        placeholders[i+offset] = undefined;
                    }
                };
                req.open("GET", dataUrl +"?offset="+ offset +"&limit="+ pageSize);
                req.setRequestHeader("Accept", "application/json");
                req.send();
                for (let n=offset; n < offset+pageSize; n++) {
                    if (placeholders[n]) {
                        observer.unobserve(placeholders[n]);
                    }
                }
            }
        }, {
            root: null,
            rootMargin: "60px",
            threshold: [0, 1]
            });

        for (let n=offset; n<offset+pageSize && n<count; n++) {
            /* create */
            let placeholder = document.createElement("a");
            placeholder.setAttribute("class", "itemLink loading");

            /* insert */
            pointer.parentNode.insertBefore(placeholder, pointer.nextSibling);

            /* update state/references */
            observer.observe(placeholder);
            placeholders[n] = placeholder;
            pointer = placeholder;
        }
    }
    marker.remove();
}

function tagState(itemCount, tagCount) {
    if (tagCount === 0) {
        return 'hidden';
    } else if (tagCount < itemCount) {
        return 'partial';
    } else {
        return 'shown';
    }
}

function updateTag(container, tag, oldState, newState) {
    for (let tagElt of container.children) {
        if (tagElt.textContent === tag) {
            if (newState === 'hidden') {
                tagElt.parentElement.removeChild(tagElt);
            } else if (newState === 'partial') {
                tagElt.classList.add('partial');
            } else {
                tagElt.classList.remove('partial');
            }
            break;
        }
    }
}

let selectionModeActive = false;
let selectionListenerRemoval = [];
function selectionMode() {
    selectionModeActive = !selectionModeActive;

    if (!selectionModeActive) {
        selectionListenerRemoval.forEach(e => e());
        selectionListenerRemoval = [];
        return;
    }

    let set = [];
    /* "tags" Element */
    let container = document.getElementsByClassName("tags")[0];
    container.style.display='block';

    selectionListenerRemoval.push(() => {
        // Remove any displayed tags, but not the "Selected item tags" text node...
        while (container.children.length) {
            container.removeChild(container.children[0]);
        }
        container.style.display='none';
    });

    /* Maps tag names to the number of occurrences in the selection. */
    let tagCounts = {};


    for (let item of document.getElementById('items').children) {
        let img = item.children[0];
        let check = document.createElement('input');
        check.setAttribute('type', 'checkbox');
        item.insertBefore(check, img);
        //  img.classList.add('selectable');

        let changehandler = () => {
            // console.log(item.dataset.id +" → "+ check.checked);
            let thisItemTags = item.dataset.tags.split(',');
            let oldItemCount = set.length;
            if (check.checked) {
                set.push(item.dataset.id);
            } else {
                set.splice(set.indexOf(item.dataset.id), 1);
            }
            let newItemCount = set.length;
            /* See if tags of other items (NOT tags of the [de]selected item) may switch between full and partial */
            for (let tag in tagCounts) {
                // Object prototype needed in case someone puts tag "hasOwnProperty" on an image... (For photos of people who own a house??)
                if (Object.prototype.hasOwnProperty.call(tagCounts, tag) && thisItemTags.indexOf(tag === -1)) {
                    let oldState = tagState(oldItemCount, tagCounts[tag]);
                    let newState = tagState(newItemCount, tagCounts[tag]);
                    updateTag(container, tag, oldState, newState);
                }
            }
            thisItemTags.forEach(thisTag => {
                let oldCount = tagCounts[thisTag] || 0;
                let oldState = tagState(newItemCount, oldCount);
                let newCount = oldCount + (check.checked ? +1 : -1);
                let newState = tagState(newItemCount, newCount);

                tagCounts[thisTag] = newCount;
                if (oldState !== newState) {
                    if (oldState === 'hidden') {
                        /* hidden -> !hidden: create element */
                        let newTag = document.createElement('span');
                        newTag.textContent = thisTag;
                        newTag.className = 'tag';
                        if (newState === 'partial') {
                            newTag.classList.add('partial');
                        }
                        container.appendChild(newTag);
                    } else {
                        updateTag(container, thisTag, oldState, newState);
                    }
                }
            });

            // console.log(set);
            // console.log(tagCounts);
        };

        check.addEventListener('click', e => {
            let newState = check.checked;

            changehandler();

            e.preventDefault(); // don't navigate link
            e.stopPropagation(); // also prevent propagation to parent <a>...
            /* Something is reversing the checkbox after this event handler. The setTimeout() is there
            * to really keep the new state. */
            setTimeout(() => check.checked = newState);
        });

        let itemClicked = e => {
                                      /* Programmatically change checkbox state when clicking on the image. */
                                      check.checked = !check.checked;
                                      e.preventDefault(); // don't navigate link
                                      changehandler();
                                  };
        item.addEventListener('click', itemClicked);
        selectionListenerRemoval.push(() => {
            item.removeChild(check);
            item.removeEventListener('click', itemClicked);
        });
    }
}