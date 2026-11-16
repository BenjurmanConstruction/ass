// Sable — Electron main process: window, native menu, file dialogs.
const { app, BrowserWindow, Menu, dialog, ipcMain } = require('electron');
const path = require('path');
const fs = require('fs/promises');

let win = null;
let dirty = false;
const isMac = process.platform === 'darwin';

function createWindow() {
  win = new BrowserWindow({
    width: 1480, height: 920, minWidth: 1060, minHeight: 640,
    backgroundColor: '#14120f', title: 'Sable', show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true, nodeIntegration: false,
      backgroundThrottling: false,              // keep the note scheduler running when the window is in the background
      autoplayPolicy: 'no-user-gesture-required',
    },
  });
  win.loadFile(path.join(__dirname, 'src', 'index.html'));
  win.once('ready-to-show', () => win.show());
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  win.webContents.on('will-navigate', (e) => e.preventDefault());
  win.on('close', (e) => {
    if (!dirty) return;
    const choice = dialog.showMessageBoxSync(win, {
      type: 'warning', buttons: ['Cancel', 'Close without saving'], defaultId: 0, cancelId: 0,
      message: 'You have unsaved changes.', detail: 'If you close now, your changes will be lost.',
    });
    if (choice === 0) e.preventDefault();
  });
  win.on('closed', () => { win = null; dirty = false; });
}

const send = (action) => () => { if (win) win.webContents.send('menu', action); };
// The renderer handles these shortcuts itself, so on Windows/Linux the accelerators are
// shown but not registered (registerAccelerator: false) to avoid firing twice.
const item = (label, accelerator, action) => ({ label, accelerator, registerAccelerator: false, click: send(action) });

function buildMenu() {
  const template = [
    ...(isMac ? [{ role: 'appMenu' }] : []),
    { label: 'File', submenu: [
      item('New Project', 'CmdOrCtrl+N', 'new'),
      item('Open…', 'CmdOrCtrl+O', 'open'),
      { type: 'separator' },
      item('Save', 'CmdOrCtrl+S', 'save'),
      item('Save As…', 'CmdOrCtrl+Shift+S', 'saveAs'),
      { type: 'separator' },
      item('Export WAV…', 'CmdOrCtrl+E', 'export'),
      { type: 'separator' },
      isMac ? { role: 'close' } : { role: 'quit' },
    ] },
    { label: 'Edit', submenu: [
      item('Undo', 'CmdOrCtrl+Z', 'undo'),
      item('Redo', 'CmdOrCtrl+Shift+Z', 'redo'),
    ] },
    { label: 'Transport', submenu: [{ label: 'Play / Stop', click: send('play') }] },
    { label: 'View', submenu: [{ role: 'togglefullscreen' }, { role: 'toggleDevTools' }] },
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

const PROJECT_FILTERS = [{ name: 'Sable Project', extensions: ['sable'] }];

ipcMain.handle('project:save', async (_e, { path: filePath, saveAs, data, name }) => {
  let target = filePath;
  if (!target || saveAs) {
    const res = await dialog.showSaveDialog(win, { title: 'Save Project', defaultPath: filePath || `${name || 'Untitled'}.sable`, filters: PROJECT_FILTERS });
    if (res.canceled || !res.filePath) return null;
    target = res.filePath;
  }
  await fs.writeFile(target, data, 'utf8');
  return target;
});

ipcMain.handle('project:open', async () => {
  const res = await dialog.showOpenDialog(win, { title: 'Open Project', properties: ['openFile'], filters: PROJECT_FILTERS });
  if (res.canceled || !res.filePaths.length) return null;
  return { path: res.filePaths[0], data: await fs.readFile(res.filePaths[0], 'utf8') };
});

ipcMain.handle('audio:export', async (_e, { name, bytes }) => {
  const res = await dialog.showSaveDialog(win, { title: 'Export WAV', defaultPath: name || 'Untitled.wav', filters: [{ name: 'WAV audio', extensions: ['wav'] }] });
  if (res.canceled || !res.filePath) return null;
  await fs.writeFile(res.filePath, Buffer.from(bytes.buffer, bytes.byteOffset, bytes.byteLength));
  return res.filePath;
});

ipcMain.on('state', (_e, { dirty: d, title }) => {
  dirty = !!d;
  if (win) { win.setTitle(title || 'Sable'); if (isMac) win.setDocumentEdited(dirty); }
});

app.whenReady().then(() => {
  buildMenu();
  createWindow();
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});
app.on('window-all-closed', () => { if (!isMac) app.quit(); });
