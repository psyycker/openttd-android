/*
 * This file is part of OpenTTD.
 * OpenTTD is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 2.
 * OpenTTD is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with OpenTTD. If not, see <https://www.gnu.org/licenses/old-licenses/gpl-2.0>.
 */

/** @file opengl_es.h OpenGL ES headers, plus the desktop OpenGL bits the OpenGL backend refers to. */

#ifndef VIDEO_OPENGL_ES_H
#define VIDEO_OPENGL_ES_H

#include <GLES3/gl32.h>

#ifndef APIENTRY
#	define APIENTRY GL_APIENTRY
#endif

/*
 * Desktop-only tokens and functions. All OpenGL functions are loaded at run
 * time and these code paths are not taken on OpenGL ES; the declarations are
 * only needed for the types of the function pointers.
 */
#define GL_TEXTURE_1D                 0x0DE0
#define GL_BGRA                       0x80E1
#define GL_UNSIGNED_INT_8_8_8_8_REV   0x8367
#define GL_MAP_PERSISTENT_BIT         0x0040
#define GL_MAP_COHERENT_BIT           0x0080
#define GL_CLIENT_STORAGE_BIT         0x0200

GL_APICALL void GL_APIENTRY glTexImage1D(GLenum target, GLint level, GLint internalformat, GLsizei width, GLint border, GLenum format, GLenum type, const void *pixels);
GL_APICALL void GL_APIENTRY glTexSubImage1D(GLenum target, GLint level, GLint xoffset, GLsizei width, GLenum format, GLenum type, const void *pixels);
GL_APICALL void *GL_APIENTRY glMapBuffer(GLenum target, GLenum access);
GL_APICALL void GL_APIENTRY glClearBufferSubData(GLenum target, GLenum internalformat, GLintptr offset, GLsizeiptr size, GLenum format, GLenum type, const void *data);
GL_APICALL void GL_APIENTRY glBufferStorage(GLenum target, GLsizeiptr size, const void *data, GLbitfield flags);
GL_APICALL void GL_APIENTRY glBindFragDataLocation(GLuint program, GLuint color, const GLchar *name);

#endif /* VIDEO_OPENGL_ES_H */
