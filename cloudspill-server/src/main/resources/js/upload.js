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
        e.preventDefault();
    },false);
}

function handleDragenter(event) {
    console.log('enter');
    //document.getElementById('drop-target');
    event.preventDefault();
    event.stopPropagation();
}

function handleDragexit(event) {
    console.log('leave');
    event.preventDefault();
    event.stopPropagation();
}

let files = [];
function handleDrop(event) {
    event.preventDefault();
    event.stopPropagation();

    files = event.dataTransfer && event.dataTransfer.files;
    console.log('Dropped', files);

    let submitEvent = document.createEvent( 'HTMLEvents' );
    submitEvent.initEvent( 'submit', false, true );
    //doUpload(
        document.getElementById('form')/*); */.dispatchEvent( submitEvent );
}

function handleSubmit(event) {
    console.log('submission event', event);
    event.preventDefault();
    doUpload(event.target);
}

function doUpload(form) {
    let formData = new FormData(form);
    let fileInput = document.getElementById('fileInput');
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