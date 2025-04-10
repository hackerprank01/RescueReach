module.exports = {
  root: true,
  env: {
    es6: true,
    node: true,
  },
  extends: [
    "eslint:recommended",
  ],
  rules: {
    // Temporarily disable rules for quick deployment
    "quotes": "off",
    "indent": "off",
    "max-len": "off",
    "comma-dangle": "off",
    "no-trailing-spaces": "off",
    "object-curly-spacing": "off",
    "arrow-parens": "off",
    "prefer-const": "off",
    "eol-last": "off",
    "new-cap": "off"
  },
  parserOptions: {
    ecmaVersion: 2018,
  },
};