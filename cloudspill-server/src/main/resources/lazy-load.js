function createPlaceholders() {
    let marker = document.getElementById("marker");
    for (let n=0; n<100; n++) {
        let placeholder = document.createElement("div");
        placeholder.setAttribute("class", "placeholder");
        marker.appendChild(placeholder);
    }

    new IntersectionObserver((entries, observer) => entries.forEach(entry => {
        if (entry.intersectionRatio > 0) {
            console.log("Trigger load");
            observer.unobserve(entry.target);
        }
    }), {
        root: null,
        rootMargin: "60px",
        threshold: [0, 1]
        }).observe(marker);
}