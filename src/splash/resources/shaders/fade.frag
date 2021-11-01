#version 150 core

out vec4 FragColor;

uniform float fade;

void main()
{
    FragColor.a = min(fade, 1.0);
}
