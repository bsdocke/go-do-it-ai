function toggleSidebar() {
    document.getElementById("sidebar").classList.toggle("open");
    document.querySelector(".overlay").classList.toggle("active");
    if (document.querySelector(".hamburger-btn")) {
        document.querySelector(".hamburger-btn").classList.toggle("open");
    }
}

document.addEventListener('DOMContentLoaded', function() {
    // Global Modal Backdrop Click Handler
    document.addEventListener('click', function(event) {
        if (event.target.tagName === 'DIALOG') {
            event.target.close();
        }
    });
});
