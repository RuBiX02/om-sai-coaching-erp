/*
 * Minimal vanilla JS for the server-rendered UI.
 * CSP-safe: no inline scripts, no event handler attributes.
 */
(function () {
  'use strict';

  var SIDEBAR_KEY = 'omsai.sidebar.collapsed';

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

    initSidebar();
  });

  /**
   * Collapsible desktop sidebar. The rail keeps every icon reachable and
   * shows the label of the hovered item, so the navigation stays usable
   * when the menu is collapsed.
   */
  function initSidebar() {
    var toggle = document.getElementById('sidebarCollapseToggle');
    var body = document.body;
    if (!toggle || !body) {
      return;
    }

    function isCollapsed() {
      return body.classList.contains('sidebar-collapsed');
    }

    function apply(collapsed) {
      body.classList.toggle('sidebar-collapsed', collapsed);
      toggle.setAttribute('aria-expanded', collapsed ? 'false' : 'true');
      var label = toggle.querySelector('span');
      if (label) {
        label.textContent = collapsed ? 'Expand menu' : 'Collapse menu';
      }
      var icon = toggle.querySelector('i');
      if (icon) {
        icon.className = collapsed ? 'bi bi-layout-sidebar-reverse' : 'bi bi-layout-sidebar';
      }
    }

    var stored = null;
    try {
      stored = window.localStorage.getItem(SIDEBAR_KEY);
    } catch (e) {
      /* storage unavailable - the default state is used */
    }
    apply(stored === 'true');

    toggle.addEventListener('click', function () {
      var collapsed = !isCollapsed();
      apply(collapsed);
      try {
        window.localStorage.setItem(SIDEBAR_KEY, collapsed ? 'true' : 'false');
      } catch (e) {
        /* storage unavailable - the choice is not remembered */
      }
    });
  }
})();
