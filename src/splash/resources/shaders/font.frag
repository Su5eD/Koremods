#version 150 core

in vec3 ourColor;
in vec2 TexCoord;

out vec4 FragColor;

uniform sampler2D ourTexture;

void main()
{
    FragColor = vec4(ourColor, texture(ourTexture, TexCoord).r - 0.15);
}
