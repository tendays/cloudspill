function login(username, tokenUrlPattern, loginUrlPattern) {
    /* First request a new token */
    let tokenQuery = new XMLHttpRequest();
    tokenQuery.onreadystatechange = () => {
        if (tokenQuery.readyState != 4) return;
        let token = tokenQuery.responseText;
        console.log('Received token '+ token);

        document.getElementsByName('name').forEach(elt => elt.innerText = username);
        //document.getElementsByName('tokenId').forEach(elt => elt.innerText = );
        document.getElementById('disconnected').style.display='none';
        document.getElementById('waiting').style.display='block';

        /* Now wait for the token to be validated */
        waitForValidation(token, loginUrlPattern.replace('%s', username));
    };
    tokenQuery.open("POST", tokenUrlPattern.replace('%s', username));
    tokenQuery.send();
}

function waitForValidation(token, loginUrl) {
    let validationQuery = new XMLHttpRequest();
    validationQuery.onreadystatechange = () => {
        if (validationQuery.readyState != 4) return;
        let status = validationQuery.responseText;
        console.log("token is: "+ status);
        if (status === 'WAITING_FOR_VALIDATION') {
            waitForValidation(token, loginUrl);
        } else if (status === 'LOGGED_IN') {
            console.log('Successfully logged as ', username);

            document.getElementById('waiting').style.display='none';
            document.getElementById('logged_in').style.display='block';
        } else {
            console.log('Unexpected response ', status);

            document.getElementById('waiting').style.display='none';
            document.getElementById('disconnected').style.display='flex';
        }
    };

    validationQuery.open("POST", loginUrl);
    validationQuery.send(token);
}