function validate(name, id) {
    document.getElementById('validate-'+ id).style.display='none';
    let stateElement = document.getElementById('state-'+ id);

    stateElement.classList.remove('invalid-chip');
    stateElement.classList.add('busy');

    validateToken(name, id, () => {
        stateElement.classList.remove('busy');
        stateElement.classList.add('valid-chip');
    });
}

function del(name, id) {
    document.getElementById('delete-'+ id).style.display='none';
    let stateElement = document.getElementById('state-'+ id);
    let rowElement = document.getElementById('row-'+ id);

    stateElement.classList.remove('invalid-chip');
    stateElement.classList.remove('valid-chip');
    stateElement.classList.add('busy');

    deleteToken(name, id, () => {
        rowElement.parentElement.removeChild(rowElement);
    });
}