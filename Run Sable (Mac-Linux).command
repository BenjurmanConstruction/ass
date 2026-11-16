#!/bin/bash
cd "$(dirname "$0")" || exit 1
if ! command -v node >/dev/null 2>&1; then
  echo "Sable needs Node.js, which is not installed on this computer."
  echo "Install the LTS version from https://nodejs.org/en/download and run this again."
  command -v open >/dev/null 2>&1 && open "https://nodejs.org/en/download"
  read -r -p "Press Enter to close."
  exit 1
fi
if [ ! -d node_modules/electron ]; then
  echo "First run: downloading Electron. This takes a minute or two..."
  npm install || { echo "Setup failed. Check your internet connection and try again."; read -r -p "Press Enter to close."; exit 1; }
fi
npm start
