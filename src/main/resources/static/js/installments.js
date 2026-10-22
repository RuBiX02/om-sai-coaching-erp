/*
 * Installment plan form: add and remove schedule rows and show the
 * running total.
 *
 * CSP-safe: no inline scripts and no event handler attributes. The
 * financial rules (at least two installments, positive amounts, the
 * sum matching the net fee) are enforced on the server - this script
 * is only a convenience.
 *
 * The preview arithmetic is done in whole cents (integers), never in
 * floating point, so a preview can never disagree with the server by
 * a rounding error. The server remains authoritative either way.
 */
(function () {
  'use strict';

  document.addEventListener('DOMContentLoaded', function () {
    var form = document.getElementById('installment-plan-form');
    if (!form) {
      return;
    }
    var table = document.getElementById('installment-rows');
    var body = table ? table.querySelector('tbody') : null;
    if (!body) {
      return;
    }

    var addButton = document.getElementById('js-add-installment');
    var totalOutput = document.getElementById('js-scheduled-total');
    var differenceOutput = document.getElementById('js-difference');
    var hint = document.getElementById('js-total-hint');
    var netFee = toCents(form.getAttribute('data-net-fee'));

    /**
     * Parses a decimal amount into whole cents. Returns null when the
     * text is not a plain amount with at most two decimal places.
     */
    function toCents(text) {
      if (text === null || text === undefined) {
        return null;
      }
      var cleaned = String(text).replace(/,/g, '.').trim();
      if (cleaned === '' || !/^\d*\.?\d*$/.test(cleaned)) {
        return null;
      }
      var parts = cleaned.split('.');
      var whole = parts[0] === '' ? '0' : parts[0];
      var fraction = parts.length > 1 ? parts[1] : '';
      if (fraction.length > 2) {
        return null;
      }
      while (fraction.length < 2) {
        fraction += '0';
      }
      var value = parseInt(whole, 10) * 100 + parseInt(fraction, 10);
      return isNaN(value) ? null : value;
    }

    /** Formats whole cents as a plain decimal amount. */
    function formatCents(cents) {
      var sign = cents < 0 ? '-' : '';
      var absolute = Math.abs(cents);
      return sign + Math.floor(absolute / 100) + '.' + String(absolute % 100).padStart(2, '0');
    }

    function rows() {
      return Array.prototype.slice.call(body.querySelectorAll('tr.installment-row'));
    }

    function renumber() {
      var all = rows();
      all.forEach(function (row, index) {
        var number = row.querySelector('.installment-number');
        if (number) {
          number.value = index + 1;
        }
        var remove = row.querySelector('.js-remove-installment');
        if (remove) {
          // A plan needs at least two installments.
          remove.disabled = all.length <= 2;
        }
      });
    }

    function updateTotal() {
      var total = 0;
      var readable = true;
      rows().forEach(function (row) {
        var field = row.querySelector('.installment-amount');
        var cents = toCents(field ? field.value : '');
        if (cents === null) {
          readable = false;
          return;
        }
        total += cents;
      });
      if (totalOutput) {
        totalOutput.textContent = formatCents(total);
      }
      var matches = readable && netFee !== null && total === netFee;
      if (differenceOutput) {
        var difference = netFee === null ? 0 : total - netFee;
        differenceOutput.textContent = (difference > 0 ? '+' : '') + formatCents(difference);
        differenceOutput.classList.remove('text-danger', 'text-success');
        differenceOutput.classList.add(matches ? 'text-success' : 'text-danger');
      }
      if (hint) {
        hint.textContent = matches
          ? 'matches the net fee'
          : 'must equal the net fee (' + (netFee === null ? '0.00' : formatCents(netFee)) + ')';
        hint.classList.remove(matches ? 'text-danger' : 'text-success');
        hint.classList.add(matches ? 'text-success' : 'text-danger');
      }
    }

    if (addButton) {
      addButton.addEventListener('click', function () {
        var all = rows();
        if (all.length === 0) {
          return;
        }
        var clone = all[all.length - 1].cloneNode(true);
        var amount = clone.querySelector('.installment-amount');
        var due = clone.querySelector('.installment-due');
        if (amount) {
          amount.value = '';
        }
        if (due) {
          due.value = '';
        }
        body.appendChild(clone);
        renumber();
        updateTotal();
      });
    }

    body.addEventListener('click', function (event) {
      var button = event.target.closest ? event.target.closest('.js-remove-installment') : null;
      if (!button || button.disabled) {
        return;
      }
      var row = button.closest('tr.installment-row');
      if (row) {
        row.remove();
      }
      renumber();
      updateTotal();
    });

    body.addEventListener('input', updateTotal);

    renumber();
    updateTotal();
  });
})();
