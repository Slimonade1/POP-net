(() => {
    for (const counter of document.querySelectorAll("[data-count-for]")) {
        const field = document.getElementById(counter.dataset.countFor);
        const update = () => {
            counter.textContent = `${field.value.length} / ${field.maxLength}`;
            counter.classList.toggle("is-full", field.value.length >= field.maxLength);
        };
        field.addEventListener("input", update);
        update();
    }

    for (const form of document.querySelectorAll("form[data-confirm]")) {
        form.addEventListener("submit", (event) => {
            if (!confirm(form.dataset.confirm)) {
                event.preventDefault();
            }
        });
    }
})();
