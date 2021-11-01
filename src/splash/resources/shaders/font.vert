#version 150 core

in vec2 aPos;
in vec2 aTexCoord;

out vec3 ourColor;
out vec2 TexCoord;

uniform vec3 textColor;
uniform mat4 view;

void main() 
{
	gl_Position = view * vec4(aPos, 0.0, 1.0);
    ourColor = textColor;
    TexCoord = aTexCoord;
}
