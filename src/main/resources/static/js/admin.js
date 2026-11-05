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

    for (const area of document.querySelectorAll(".compose textarea")) {
        const fit = () => {
            area.style.height = "auto";
            area.style.height = area.scrollHeight + area.offsetHeight - area.clientHeight + "px";
        };
        area.addEventListener("input", fit);
        fit();
    }

    for (const button of document.querySelectorAll("[data-step]")) {
        button.addEventListener("click", () => {
            const input = button.parentElement.querySelector("input");
            if (button.dataset.step === "up") {
                input.stepUp();
            } else {
                input.stepDown();
            }
        });
    }

    for (const form of document.querySelectorAll("form[data-confirm]")) {
        form.addEventListener("submit", (event) => {
            if (!confirm(form.dataset.confirm)) {
                event.preventDefault();
            }
        });
    }
})();
