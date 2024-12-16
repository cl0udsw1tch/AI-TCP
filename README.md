## 🤖 AI Time Complexity Predictor ⏲️

### Runs as a VSCode extension 💻

Inside VSCode select a Java source code file and highlight either
1. A proper compilation unit (class name with method)
2. Block of code (surrounded by braces)

Use the machine learning model to predict the complexity as either
- O(1)
- O(n)
- O(nlogn)
- O(logn)
- O(n^2)
- O(n^3)
- NP-hard

The model is stored in the `models` folder. 🤖

The preprocessor is in the `code2ast/target` folder as the .jar file 📁