const { getDefaultConfig } = require('expo/metro-config');

const { withVarlockMetroConfig } = require('@varlock/expo-integration/metro-config');

module.exports = withVarlockMetroConfig(getDefaultConfig(__dirname));
