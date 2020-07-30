var username;

function login(newUsername, tokenUrlPattern, loginUrl) {
    username = newUsername;
    /* First request a new token */
    let tokenQuery = new XMLHttpRequest();
    tokenQuery.onreadystatechange = () => {
        if (tokenQuery.readyState != 4) return;
        let token = tokenQuery.responseText;
        console.log('Received token '+ token);

        let tokenId = token.substring(0, token.indexOf(':'));
        document.getElementsByName('name').forEach(elt => elt.innerText = username);
        document.getElementsByName('tokenId').forEach(elt => elt.innerText = tokenId);
        document.getElementById('disconnected').style.display='none';
        document.getElementById('waiting').style.display='block';

        /* Now wait for the token to be validated */
        waitForValidation(token, loginUrl);
    };
    tokenQuery.open("POST", tokenUrlPattern.replace('{username}', username));
    tokenQuery.send();
}

function logout(logoutUrl) {
    let logoutQuery = new XMLHttpRequest();
    logoutQuery.onreadystatechange = () => {
        if (logoutQuery.readyState != 4) return;
        document.getElementById('disconnected').style.display='flex';
        document.getElementById('waiting').style.display='none';
        document.getElementById('logged_in').style.display='none';
    };
    logoutQuery.open("POST", logoutUrl);
    logoutQuery.send();
}

function waitForValidation(token, loginUrl) {
    let validationQuery = new XMLHttpRequest();
    validationQuery.onreadystatechange = () => {
        if (validationQuery.readyState === 4 && validationQuery.status !== 0) {
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
        }
    };

    validationQuery.open("POST", loginUrl);
    validationQuery.send(token);
}