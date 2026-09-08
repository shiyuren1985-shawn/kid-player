# 0.8.0 桌面图标素材

使用内置 ImageGen 生成 7 款新图案，保留原有萌可素材。

| 资源 | 图案 |
| --- | --- |
| launcher_kid.webp | 原创 Kid Player 微笑播放标志 |
| launcher_ultra.webp | 奥特曼 |
| launcher_robot.webp | 擎天柱 |
| launcher_moon.webp | 美少女战士 |
| launcher_dora.webp | 哆啦 A 梦 |
| launcher_bear.webp | 原创太空小熊 |
| launcher_dino.webp | 原创小恐龙 |

资源位于 `app/src/main/res/drawable-nodpi`，均为 512×512 RGB WebP，总计约 105 KiB。作为 adaptive icon 的整幅背景使用，前景透明；选择页使用对应 adaptive mipmap，以保持和桌面相同的裁切方式。

整体采用清晰、友好的卡通画法，突出轮廓和主体，背景铺满，不含文字或水印。原创 Kid Player 是青绿色微笑播放三角，搭配珊瑚色与黄色点缀；太空小熊使用白色头盔与靛蓝背景；绿色三角龙搭配暖黄色背景。第二轮调整缩小完整主体、保留原画风和背景，使图案适应系统裁切。

在隔离子 Agent 中检查图像中央 72/108 范围，再施加圆形遮罩，以 160、96、48 像素检查小尺寸可读性。7 款最终图案均通过：面部、头部、发饰及主要轮廓完整，无需额外 inset 包装。原始生成与审图资料位于忽略提交的 `qa/launcher-icons-0.8.0`，其中 `assets.json` 保存最终尺寸和 SHA256。
