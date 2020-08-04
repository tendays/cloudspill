'use strict';

function setupDnd() {
    window.addEventListener("dragover",function(e){
        console.log('Hello from global dragover listener', e);
        e = e || event;
        e.preventDefault();
    },false);
    window.addEventListener("drop",function(e){
        console.log('Hello from global drop listener', e);
        e = e || event;
        handleDrop(e);
    },false);
}

let files = [];
function handleDrop(event) {
    event.preventDefault();
    event.stopPropagation();

    files = event.dataTransfer && event.dataTransfer.files;
    console.log('Dropped', files);

    doUpload();
}

function doUpload() {
    let formData = new FormData();
    Array.prototype.forEach.call( files, file => {
        formData.append('files[]', file);
    });

    var uploadRequest = new XMLHttpRequest();
    uploadRequest.open("POST", "/lab");
    uploadRequest.onreadystatechange = () => {
        if (uploadRequest.readyState != 4) return;
        console.log(uploadRequest.status, uploadRequest.responseText);
    };
    uploadRequest.send(formData);
}