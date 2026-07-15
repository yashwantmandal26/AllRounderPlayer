with open(r'c:\Users\Yashwant\Desktop\YMedia Player\app\src\main\java\com\example\ymediaplayer\ui\VideoPlayerScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('import android.widget.FrameLayout', 'import android.widget.FrameLayout\nimport android.view.ViewGroup')
content = content.replace('icon?.let { Icon(it, contentDescription = null, tint = PlayerWhite, modifier = Modifier.size(48.dp)) }', 'gestureIcon?.let { Icon(it, contentDescription = null, tint = PlayerWhite, modifier = Modifier.size(48.dp)) }')

with open(r'c:\Users\Yashwant\Desktop\YMedia Player\app\src\main\java\com\example\ymediaplayer\ui\VideoPlayerScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)
