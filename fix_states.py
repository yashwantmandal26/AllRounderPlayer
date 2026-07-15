with open(r'c:\Users\Yashwant\Desktop\YMedia Player\app\src\main\java\com\example\ymediaplayer\ui\VideoPlayerScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

if 'var showContinueBanner by remember' not in content:
    content = content.replace('var isLocked by remember { mutableStateOf(false) }',
                              'var isLocked by remember { mutableStateOf(false) }\n    var showContinueBanner by remember { mutableStateOf(true) }')

if 'var playbackSpeed by remember { mutableFloatStateOf(1f) }' not in content and 'var playbackSpeed by remember { mutableFloatStateOf(1.0f) }' not in content:
    content = content.replace('var isLocked by remember { mutableStateOf(false) }',
                              'var isLocked by remember { mutableStateOf(false) }\n    var playbackSpeed by remember { mutableFloatStateOf(1f) }')

with open(r'c:\Users\Yashwant\Desktop\YMedia Player\app\src\main\java\com\example\ymediaplayer\ui\VideoPlayerScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("States checked.")
