function newComment(id) {
    /* field elements */
    const authorElt = document.getElementById('new-comment-author');
    const textElt = document.getElementById('new-comment-text');

    /* field values */
    const author = authorElt.value;
    const text = textElt.value;
    textElt.value = '';

    /* Insert new comment into DOM */
    const commentBlock = document.createElement('div');
    commentBlock.className = 'comment';

    const authorBlock = document.createElement('div');
    authorBlock.className = 'comment-author';
    authorBlock.innerText = 'By: '+ author;
    commentBlock.appendChild(authorBlock);

    const postedBlock = document.createElement('div');
    commentBlock.appendChild(postedBlock);

    const textBlock = document.createElement('div');
    textBlock.className = 'comment-text';
    textBlock.innerText = text;
    commentBlock.appendChild(textBlock);

    const commentsElt = document.getElementById('comments');
    // insert new comment before all others
    commentsElt.insertBefore(commentBlock, commentsElt.firstChild);

    authorBlock.classList.add('busy');

    postComment(id, author, text, res => {
        /* The response is the creation date recorded by the back-end */
        authorBlock.classList.remove('busy');

        const d = new Date(res);
        // left-pad each component with a zero
        postedBlock.innerText = d.getFullYear() +'-'+
        ('0'+d.getMonth()).slice(-2) +'-'+ ('0'+d.getDate()).slice(-2) +' '+
            ('0'+d.getHours()).slice(-2) +':'+ ('0'+d.getMinutes()).slice(-2) +':'+ ('0'+d.getSeconds()).slice(-2);
        // Only add CSS when date is available, otherwise the calendar icon looks funny all alone
        postedBlock.className = 'comment-posted';
    });
}