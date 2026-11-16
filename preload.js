// Sable — preload: the only bridge between the UI and the desktop.
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('native', {
  save: (opts) => ipcRenderer.invoke('project:save', opts),
  open: () => ipcRenderer.invoke('project:open'),
  exportWav: (opts) => ipcRenderer.invoke('audio:export', opts),
  readSample: (rel) => ipcRenderer.invoke('sample:read', rel),
  setState: (state) => ipcRenderer.send('state', state),
  onMenu: (fn) => ipcRenderer.on('menu', (_e, action) => fn(action)),
});
