#!/bin/bash
cd "$(dirname "$0")" || exit 1
if ! command -v node >/dev/null 2>&1; then
  echo "Building needs Node.js, which is not installed on this computer."
  echo "Install the LTS version from https://nodejs.org/en/download and run this again."
  command -v open >/dev/null 2>&1 && open "https://nodejs.org/en/download"
  read -r -p "Press Enter to close."
  exit 1
fi
echo "Installing build tools..."
npm install || { echo "Setup failed."; read -r -p "Press Enter to close."; exit 1; }
echo "Building the installer. This takes a few minutes..."
npm run dist || { echo "The build failed."; read -r -p "Press Enter to close."; exit 1; }
echo
echo "Done. The installer is in the dist folder."
command -v open >/dev/null 2>&1 && open dist
read -r -p "Press Enter to close."
