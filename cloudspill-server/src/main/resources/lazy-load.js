function createPlaceholders() {
    let marker = document.getElementById("marker");
    for (let n=0; n<100; n++) {
        let placeholder = document.createElement("div");
        placeholder.setAttribute("class", "placeholder");
        marker.appendChild(placeholder);
    }
}