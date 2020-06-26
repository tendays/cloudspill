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
            placeholder.setAttribute("class", "thumb loading");

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