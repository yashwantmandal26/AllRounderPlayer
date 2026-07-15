with open(r'c:\Users\Yashwant\Desktop\YMedia Player\app\src\main\java\com\example\ymediaplayer\theme\ThemeController.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('fun setMode(newMode: ThemeMode)', 'fun updateMode(newMode: ThemeMode)')
content = content.replace('setMode(if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK)', 'updateMode(if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK)')

with open(r'c:\Users\Yashwant\Desktop\YMedia Player\app\src\main\java\com\example\ymediaplayer\theme\ThemeController.kt', 'w', encoding='utf-8') as f:
    f.write(content)
