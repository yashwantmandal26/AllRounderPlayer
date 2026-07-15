import os

directory = r'c:\Users\Yashwant\Desktop\YMedia Player\app\src\main\java\com\example\ymediaplayer'

for root, _, files in os.walk(directory):
    for file in files:
        if file.endswith('.kt'):
            path = os.path.join(root, file)
            with open(path, 'r', encoding='utf-8') as f:
                content = f.read()
            if '.setMode(' in content:
                content = content.replace('.setMode(', '.updateMode(')
                with open(path, 'w', encoding='utf-8') as f:
                    f.write(content)
                print(f"Updated {file}")
