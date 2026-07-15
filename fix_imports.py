with open(r'c:\Users\Yashwant\Desktop\YMedia Player\app\src\main\java\com\example\ymediaplayer\ui\VideoPlayerScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

imports_to_add = [
    'import androidx.compose.material.icons.rounded.Cast',
    'import androidx.compose.material.icons.rounded.ClosedCaption',
    'import androidx.compose.material.icons.rounded.PlaylistPlay',
    'import androidx.compose.material.icons.rounded.MoreVert',
    'import androidx.compose.material.icons.rounded.Headset',
    'import androidx.compose.material.icons.rounded.ChevronRight',
    'import androidx.compose.material.icons.rounded.Close',
    'import androidx.compose.material.icons.rounded.SkipPrevious',
    'import androidx.compose.material.icons.rounded.SkipNext',
    'import androidx.compose.material.icons.rounded.PictureInPictureAlt'
]

lines = content.split('\n')
import_idx = 0
for i, line in enumerate(lines):
    if line.startswith('import '):
        import_idx = i

for imp in imports_to_add:
    if imp not in content:
        lines.insert(import_idx, imp)

with open(r'c:\Users\Yashwant\Desktop\YMedia Player\app\src\main\java\com\example\ymediaplayer\ui\VideoPlayerScreen.kt', 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines))

print("Imports checked/added.")
