'use strict';

function setupDnd(imageUrlPattern, hrefPattern) {
    window.addEventListener("dragover",function(e){
        console.log('Hello from global dragover listener', e);
        e = e || event;
        e.preventDefault();
    },false);
    window.addEventListener("drop",function(e){
        console.log('Hello from global drop listener', e);
        e = e || event;
        handleDrop(e, imageUrlPattern, hrefPattern);
    },false);
}

let files = [];
function handleDrop(event, imageUrlPattern, hrefPattern) {
    event.preventDefault();
    event.stopPropagation();

    files = event.dataTransfer && event.dataTransfer.files;
    console.log('Dropped', files);

    let formData = new FormData();
    Array.prototype.forEach.call( files, file => {
        formData.append('files[]', file);
    });

    var uploadRequest = new XMLHttpRequest();
    uploadRequest.open("POST", "/lab");
    uploadRequest.onreadystatechange = () => {
        if (uploadRequest.readyState != 4) return;

        let drawer = document.getElementById('drawer');
        drawer.style.display = 'block';
        JSON.parse(uploadRequest.responseText).forEach(item => {
            let thumbnail = document.createElement('a');
            thumbnail.href = hrefPattern.replace("%d", item);
            thumbnail.target = "_blank";
            thumbnail.innerHTML = "<img class='drawer-thumb', src='"+ imageUrlPattern.replace("%d", item) +"'>";
            drawer.appendChild(thumbnail);
        });
        console.log(uploadRequest.status, uploadRequest.responseText);
    };
    uploadRequest.send(formData);
}