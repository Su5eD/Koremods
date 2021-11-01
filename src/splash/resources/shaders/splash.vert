#version 150 core

in vec2 aPos;
in vec3 aColor;
in vec2 aTexCoord;

out vec3 ourColor;
out vec2 TexCoord;

void main()
{
    gl_Position = vec4(aPos, 0.0, 1);
    ourColor = aColor;
    TexCoord = aTexCoord;
}
