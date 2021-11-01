#version 150 core

#define PI 3.14159265

uniform vec2 resolution;
uniform float time;

out vec4 fragColor;

const float circles = 4.0;
const float size = 25.0;
const float dotRadius = (size / 90.0) * 0.015;
const float offset = 7.0;
const float slow = 0.7;

const float radOffset = PI / 2.0;

vec4 circle(vec2 uv, vec2 pos, float rad, vec3 color)
{
	float d = length(pos - uv) - rad;
	float t = clamp(d, 0.0, 1.0);
    return vec4(color, 1.0 - t);
}

vec3 rgb(float r, float g, float b)
{
	return vec3(r / 255.0, g / 255.0, b / 255.0);
}

void main()
{
    vec3 backCol = rgb(0.0, 0.0, 0.0);
    vec3 backCirCol = rgb(30.0, 20.0, 50.0);
    vec3 rotCirCol = rgb(255.0, 255.0, 255.0);

   	vec2 uv = gl_FragCoord.xy;
	float radiusRot = dotRadius * 512.0;
    vec2 center = vec2(resolution.x * 0.91, resolution.y * 0.15);
    
    for(float i = 0.0; i < circles; i++)
    {
        float actualTime = max(time - i * 0.1, 0.0);
        float timeOffset = actualTime * offset + radOffset;
        float speed = cos(timeOffset) * slow;
        
        float point = timeOffset + speed + radOffset;
        float y = cos(point);
        float x = sin(point);
        vec2 centerRot = center + vec2(x * size, y * size);
        
        vec4 rotCircle = circle(uv, centerRot, radiusRot, rotCirCol);
        fragColor = mix(fragColor, rotCircle, rotCircle.a);
    }
}
