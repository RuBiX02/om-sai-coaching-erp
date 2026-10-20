/*
 * Minimal vanilla JS for the server-rendered UI.
 * CSP-safe: no inline scripts, no event handler attributes.
 */
(function () {
  'use strict';

  document.addEventListener('DOMContentLoaded', function () {
    // Confirmation dialogs for destructive forms/buttons.
    document.querySelectorAll('button.js-confirm').forEach(function (el) {
      el.addEventListener('click', function (event) {
        var message = el.getAttribute('data-confirm') || 'Are you sure?';
        if (!window.confirm(message)) {
          event.preventDefault();
          event.stopPropagation();
        }
      });
    });

    // Print buttons on the receipt page.
    document.querySelectorAll('button.js-print').forEach(function (el) {
      el.addEventListener('click', function () {
        window.print();
      });
    });

    // Auto-dismiss success alerts after a few seconds.
    document.querySelectorAll('.alert-dismissible').forEach(function (el) {
      if (!el.classList.contains('alert-danger')) {
        window.setTimeout(function () {
          try {
            var inst = window.bootstrap && window.bootstrap.Alert;
            if (inst) {
              inst.getOrCreateInstance(el).close();
            }
          } catch (e) {
            /* alert already removed */
          }
        }, 6000);
      }
    });
  });
})();
