function login(username, tokenUrlPattern, loginUrlPattern) {
    /* First request a new token */
    let tokenQuery = new XMLHttpRequest();
    tokenQuery.onreadystatechange = () => {
        if (tokenQuery.readyState != 4) return;
        let token = tokenQuery.responseText;
        console.log('Received token '+ token);

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
        if (status === 'invalid') {
            waitForValidation(token, loginUrl);
        } else {
            console.log('Successfully logged as ', username);
        }
    };

    validationQuery.open("POST", loginUrl);
    validationQuery.send(token);
}