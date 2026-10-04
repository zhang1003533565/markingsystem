import { useEffect, useRef } from 'react';
import { EditorContent, ReactNodeViewRenderer, useEditor } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Image from '@tiptap/extension-image';
import Placeholder from '@tiptap/extension-placeholder';
import TextAlign from '@tiptap/extension-text-align';
import { TextStyle } from '@tiptap/extension-text-style';
import { Extension, Node, mergeAttributes } from '@tiptap/core';
import { message } from 'antd';
import AnnouncementResizableImage from './AnnouncementResizableImage';

const VideoNode = Node.create({
  name: 'video',
  group: 'block',
  atom: true,
  selectable: true,

  addAttributes() {
    return {
      src: { default: null },
      controls: { default: true }
    };
  },

  parseHTML() {
    return [{ tag: 'video' }];
  },

  renderHTML({ HTMLAttributes }) {
    return ['video', mergeAttributes(HTMLAttributes, { controls: 'true', class: 'announcement-editor-video-node' })];
  }
});

const FontSize = Extension.create({
  name: 'fontSize',

  addOptions() {
    return {
      types: ['textStyle']
    };
  },

  addGlobalAttributes() {
    return [
      {
        types: this.options.types,
        attributes: {
          size: {
            default: null,
            parseHTML: (element) => element.style.fontSize?.replace('px', '') || null,
            renderHTML: (attributes) => {
              if (!attributes.size) {
                return {};
              }
              return {
                style: `font-size: ${attributes.size}px`
              };
            }
          }
        }
      }
    ];
  },

  addCommands() {
    return {
      setFontSize: (size) => ({ chain }) => {
        if (!size) {
          return chain().setMark('textStyle', {}).removeEmptyTextStyle().run();
        }
        return chain().setMark('textStyle', { size: String(size) }).run();
      }
    };
  }
});

const fontSizeOptions = ['14', '16', '18', '22', '28'];

const ResizableImage = Image.extend({
  inline() {
    return true;
  },

  group() {
    return 'inline';
  },

  addAttributes() {
    return {
      ...this.parent?.(),
      width: {
        default: null,
        parseHTML: (element) => {
          const width = element.getAttribute('data-width') || element.style.width?.replace('px', '');
          return width ? Number(width) : null;
        },
        renderHTML: (attributes) => {
          if (!attributes.width) {
            return {};
          }
          return {
            'data-width': attributes.width,
            style: `width: ${attributes.width}px`
          };
        }
      },
      align: {
        default: 'left',
        parseHTML: (element) => element.getAttribute('data-align') || 'left',
        renderHTML: (attributes) => ({
          'data-align': attributes.align || 'left'
        })
      }
    };
  },

  addNodeView() {
    return ReactNodeViewRenderer(AnnouncementResizableImage);
  }
});

function AnnouncementRichEditor({ value, onChange }) {
  const imageInputRef = useRef(null);
  const videoInputRef = useRef(null);

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: {
          levels: [1, 2, 3]
        }
      }),
      TextStyle,
      FontSize,
      TextAlign.configure({
        types: ['heading', 'paragraph']
      }),
      ResizableImage.configure({
        allowBase64: true,
        HTMLAttributes: {
          class: 'announcement-editor-image-node'
        }
      }),
      VideoNode,
      Placeholder.configure({
        placeholder: '请输入公告内容，支持直接粘贴图片或视频...'
      })
    ],
    content: value || '',
    immediatelyRender: false,
    onUpdate: ({ editor: currentEditor }) => {
      onChange(currentEditor.getHTML());
    },
    editorProps: {
      handlePaste(view, event) {
        const items = Array.from(event.clipboardData?.items || []);
        const fileItem = items.find((item) => item.kind === 'file');
        const file = fileItem?.getAsFile();
        if (!file) {
          return false;
        }

        const isImage = file.type.startsWith('image/');
        const isVideo = file.type.startsWith('video/');
        if (!isImage && !isVideo) {
          return false;
        }

        const reader = new FileReader();
        reader.onload = () => {
          if (isImage) {
            editor?.chain().focus().setImage({ src: reader.result, width: 420 }).run();
            message.success('已粘贴图片');
          } else {
            editor?.chain().focus().insertContent({ type: 'video', attrs: { src: reader.result } }).run();
            message.success('已粘贴视频');
          }
        };
        reader.readAsDataURL(file);
        event.preventDefault();
        return true;
      }
    }
  });

  useEffect(() => {
    if (!editor) {
      return;
    }
    const current = editor.getHTML();
    if ((value || '') !== current) {
      editor.commands.setContent(value || '', false);
    }
  }, [editor, value]);

  const insertFile = (field) => async (e) => {
    const selectedFile = e.target.files?.[0];
    if (!selectedFile || !editor) {
      return;
    }

    const isImage = field === 'image';
    if (isImage && !selectedFile.type.startsWith('image/')) {
      message.error('请上传图片文件');
      return;
    }
    if (!isImage && !selectedFile.type.startsWith('video/')) {
      message.error('请上传视频文件');
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      if (isImage) {
        editor.chain().focus().setImage({ src: reader.result, width: 420 }).run();
      } else {
        editor.chain().focus().insertContent({ type: 'video', attrs: { src: reader.result } }).run();
      }
    };
    reader.readAsDataURL(selectedFile);
    e.target.value = '';
  };

  const clearContent = () => {
    editor?.commands.setContent('');
    onChange('');
  };

  const currentFontSize = editor?.getAttributes('textStyle')?.size || '';

  const applyFontSize = (value) => {
    const nextValue = String(value || '').trim();
    if (!nextValue) {
      editor?.chain().focus().setFontSize('').run();
      return;
    }

    const sizeNumber = Number(nextValue);
    if (Number.isNaN(sizeNumber) || sizeNumber < 10 || sizeNumber > 96) {
      message.warning('字号请输入 10 到 96 之间的数字');
      return;
    }

    editor?.chain().focus().setFontSize(String(sizeNumber)).run();
  };

  const adjustFontSize = (delta) => {
    const baseSize = Number(currentFontSize || 16);
    const nextSize = Math.max(10, Math.min(96, baseSize + delta));
    editor?.chain().focus().setFontSize(String(nextSize)).run();
  };

  const applyImageAlignment = (align) => {
    if (!editor || !editor.isActive('image')) {
      message.info('请先选中图片');
      return;
    }
    editor.chain().focus().updateAttributes('image', { align }).run();
  };

  return (
    <div className="rich-editor-shell">
      <div className="rich-editor-toolbar">
        <div className="rich-editor-toolbar-group">
          <button type="button" className="btn btn-default btn-sm" onClick={() => editor?.chain().focus().toggleHeading({ level: 1 }).run()}>
            标题1
          </button>
          <button type="button" className="btn btn-default btn-sm" onClick={() => editor?.chain().focus().toggleHeading({ level: 2 }).run()}>
            标题2
          </button>
          <button type="button" className="btn btn-default btn-sm" onClick={() => editor?.chain().focus().toggleHeading({ level: 3 }).run()}>
            标题3
          </button>
          <button type="button" className="btn btn-default btn-sm" onClick={() => editor?.chain().focus().toggleBold().run()}>
            加粗
          </button>
          <button type="button" className="btn btn-default btn-sm" onClick={() => editor?.chain().focus().toggleItalic().run()}>
            斜体
          </button>
          <button type="button" className="btn btn-default btn-sm" onClick={() => editor?.chain().focus().toggleBulletList().run()}>
            列表
          </button>
          <button type="button" className="btn btn-default btn-sm" onClick={() => editor?.chain().focus().setParagraph().run()}>
            段落
          </button>
          <select
            className="rich-editor-select"
            value={currentFontSize || ''}
            onChange={(e) => applyFontSize(e.target.value)}
          >
            <option value="">默认字号</option>
            {Array.from({ length: 87 }, (_, index) => {
              const size = String(index + 10);
              return (
                <option key={size} value={size}>
                  {size}
                </option>
              );
            })}
          </select>
          <button type="button" className="btn btn-default btn-sm" onClick={() => adjustFontSize(-2)}>
            减小字号
          </button>
          <button type="button" className="btn btn-default btn-sm" onClick={() => adjustFontSize(2)}>
            增大字号
          </button>
          <button type="button" className="btn btn-default btn-sm" onClick={() => editor?.chain().focus().setTextAlign('left').run()}>
            左对齐
          </button>
          <button type="button" className="btn btn-default btn-sm" onClick={() => editor?.chain().focus().setTextAlign('center').run()}>
            居中
          </button>
          <button type="button" className="btn btn-default btn-sm" onClick={() => editor?.chain().focus().setTextAlign('right').run()}>
            右对齐
          </button>
          <button type="button" className="btn btn-default btn-sm" onClick={() => applyImageAlignment('left')}>
            图片居左
          </button>
          <button type="button" className="btn btn-default btn-sm" onClick={() => applyImageAlignment('center')}>
            图片居中
          </button>
          <button type="button" className="btn btn-default btn-sm" onClick={() => applyImageAlignment('right')}>
            图片居右
          </button>
        </div>
        <div className="rich-editor-toolbar-group">
          <input
            ref={imageInputRef}
            type="file"
            accept="image/*"
            className="hidden-file-input"
            onChange={insertFile('image')}
          />
          <input
            ref={videoInputRef}
            type="file"
            accept="video/*"
            className="hidden-file-input"
            onChange={insertFile('video')}
          />
          <button type="button" className="btn btn-default btn-sm" onClick={() => imageInputRef.current?.click()}>
            插入图片
          </button>
          <button type="button" className="btn btn-default btn-sm" onClick={() => videoInputRef.current?.click()}>
            插入视频
          </button>
          <button type="button" className="btn btn-default btn-sm" onClick={clearContent}>
            清空
          </button>
        </div>
      </div>

      <div className="rich-editor-content">
        <EditorContent editor={editor} />
      </div>
    </div>
  );
}

export default AnnouncementRichEditor;
