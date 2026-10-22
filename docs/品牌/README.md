# 星语品牌资产

显示名称：星语。标语：和更懂你的 AI 聊天。

用户提供的 [品牌参考](reference.png) 是制作依据。采用内置 imagegen 编辑上方猫咪抱星星的插画，去除海报白边、文字与圆角框，再单独缩小主体，为系统图标裁切留出空间。最终 [方形图标母版](xingyu-icon.png) 为 1254 × 1254 PNG。

Android 使用 [512px WebP 资源](../../app/src/main/res/drawable-nodpi/ic_xingyu_art.webp)，只对生成结果缩放与编码，没有重新绘制或裁切主体。[自适应图标](../../app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml) 由系统裁切外形，设置页使用相同图像；[通知图标](../../app/src/main/res/drawable/ic_xingyu_notification.xml) 为 Android 所需的单色星形。

应用包名、签名、版本与存储身份保持一致，覆盖安装原应用。交付文件仍使用原来的文件名，桌面和应用内显示名称统一为星语。

## 内置 imagegen 提示词

首次编辑（参考图 → 方形图标）：

```text
Use case: precise-object-edit. Asset type: Android launcher icon for the app 星语. Input image: supplied 星语 brand poster, edit target. Adapt ONLY its upper illustrated kitten-and-star artwork into one square, full-bleed launcher icon. Remove all white poster margins, the bottom Chinese and English title, the slogan, and the existing rounded-square framing. Extend the illustrated lavender night background smoothly to all four square edges. Preserve the exact fluffy ivory kitten identity: pink ear interiors, cream curl, one winking eye, one violet eye with star reflection, rosy cheeks, soft blue-lavender ribbon, paws embracing the glowing peach-gold five-point star, and the pink heart speech bubble. Preserve the soft luminous original painting style and pastel palette. Reframe the whole kitten face, both ears, paws and glowing star as a balanced compact center composition with about 18 percent illustrated breathing room around them so the important subject remains readable under Android circular and rounded-square launcher masks. Peripheral stars and clouds may occupy the outer area. No text, letters, branding words, watermarks, borders, white margins or pre-applied rounded corners. Opaque square artwork, suitable for app icon use.
```

最后编辑（方形图标 → 系统裁切安全构图）：

```text
Use case: precise-object-edit. Edit the supplied square kitten/star app icon. Change ONLY composition scale: zoom the existing kitten, heart bubble and large held star out so their entire combined silhouette fits fully inside the central 64 percent of the square canvas. Add wide illustrated night-sky and cloud breathing room around all four sides, continuing the same backdrop to the edges. The top of both ears must be below y=20%, the right ear must remain left of x=82%, and the complete large held star must remain above y=82%. Preserve the exact same kitten face, wink, violet star eye, fluffy curl, paws, held star, ribbon, heart bubble and luminous pastel painterly artwork. Keep a centered readable icon composition. This is required to prevent Android adaptive circular masks clipping the ears or star. Do not introduce any extra characters, lettering, titles, slogans, frames, borders or white margins. Full-bleed opaque square image.
```

