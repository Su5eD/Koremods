#version 150 core

in vec3 ourColor;
in vec2 TexCoord;

out vec4 FragColor;

const float fadeY = 120;
const float fadeHeight = 30;

uniform sampler2D ourTexture;

void main()
{
    FragColor = vec4(ourColor, texture(ourTexture, TexCoord).r - 0.05);
    if (gl_FragCoord.y >= fadeY) {
        float diff = gl_FragCoord.y - fadeY;
        FragColor.a -= diff / fadeHeight;
    }
}
