// Browser-side helpers exercise the real ARIA trigger and listbox, including option clicks.
const selectSupport = `<script>
window.selectPopup = async control => {
  await new Promise(resolve => requestAnimationFrame(resolve));
  if (control.getAttribute('aria-expanded') !== 'true') control.click();
  for (let attempt = 0; attempt < 200; attempt++) {
    const popup = document.getElementById(control.getAttribute('aria-controls'));
    if (popup) return popup;
    await new Promise(resolve => setTimeout(resolve, 10));
  }
  throw new Error('Combobox popup did not open: ' + control.getAttribute('aria-label'));
};
window.menuSelectOptions = async (control, value) => {
  const popup = await selectPopup(control);
  const options = [];
  const collect = panel => [...panel.querySelectorAll('[role="menuitemradio"]')];
  const visit = async panel => {
    for (const option of collect(panel)) {
      options.push({ value: option.getAttribute('data-value'), textContent: option.textContent, disabled: option.getAttribute('aria-disabled') === 'true' });
      if (value !== undefined && option.getAttribute('data-value') === String(value)) {
        if (option.getAttribute('aria-disabled') === 'true') throw Error('Disabled menu choice: ' + value);
        option.click(); return true;
      }
    }
    return false;
  };
  if (await visit(popup)) return options;
  for (const branch of popup.querySelectorAll('[aria-haspopup="menu"]')) {
    branch.dispatchEvent(new MouseEvent('mouseenter'));
    branch.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
    let panel;
    for (let i = 0; i < 200; i++) {
      panel = document.getElementById(branch.getAttribute('aria-controls'));
      if (panel) break;
      await new Promise(resolve => setTimeout(resolve, 10));
    }
    if (!panel) throw Error('Missing selection submenu');
    if (await visit(panel)) return options;
    branch.click();
    await new Promise(resolve => setTimeout(resolve, 0));
  }
  control.click();
  if (value !== undefined) throw Error('Missing menu choice: ' + value);
  return options;
};
window.selectOptions = async control => {
  if (control.matches('vc-menu-select')) return menuSelectOptions(control);
  const wasOpen = control.getAttribute('aria-expanded') === 'true';
  const popup = await selectPopup(control);
  const options = [...popup.querySelectorAll('[role="option"]')].map(option => ({
    value: option.getAttribute('data-value') ?? '', textContent: option.textContent,
    disabled: option.getAttribute('aria-disabled') === 'true'
  }));
  if (!wasOpen) {
    control.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    await new Promise(resolve => setTimeout(resolve, 0));
  }
  return options;
};
window.changeControl = async (control, value) => {
  if (control.matches('vc-menu-select')) {
    await menuSelectOptions(control, value);
    await new Promise(resolve => setTimeout(resolve, 0));
  } else if (control.matches('vc-select')) {
    const popup = await selectPopup(control);
    const option = [...popup.querySelectorAll('[role="option"]')].find(option => option.getAttribute('data-value') === String(value));
    if (!option) throw new Error('Missing combobox choice: ' + value + ' for ' + control.getAttribute('aria-label'));
    if (option.getAttribute('aria-disabled') === 'true') throw new Error('Disabled combobox choice: ' + value);
    option.click();
    for (let attempt = 0; attempt < 200 && control.getAttribute('aria-expanded') === 'true'; attempt++) await new Promise(resolve => setTimeout(resolve, 10));
  } else {
    control.value = value;
    control.dispatchEvent(new Event('change', { bubbles: true }));
  }
};
</script>`;
module.exports = { selectSupport };
