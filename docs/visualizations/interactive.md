---
title: "Interactive Visualizations"
subtitle: "Google Studio-Inspired p5.js Visualizations for Architecture Understanding"
version: "2.0.0"
date: "2026-02-01"
author: "Technical Architecture Team"
classification: "Educational"
status: "Active"
---

# Interactive Visualizations

## Table of Contents

1. [Introduction](#introduction)
2. [Architecture Flow Visualization](#architecture-flow-visualization)
3. [Token Generation Performance Monitor](#token-generation-performance-monitor)
4. [Memory Usage Dashboard](#memory-usage-dashboard)
5. [Cache Hit Rate Visualization](#cache-hit-rate-visualization)
6. [Design Patterns Constellation](#design-patterns-constellation)
7. [Usage Instructions](#usage-instructions)
8. [Design System](#design-system)
9. [Related Documents](#related-documents)

---

## Introduction

### Purpose

This document provides interactive p5.js visualizations designed with a **Google Studio-inspired aesthetic** to help understand the Nimittam system architecture, data flows, and runtime behavior. These visualizations feature:

- **Material Design 3** principles
- **Google's signature color palette** (Blue, Red, Yellow, Green)
- **Clean, minimalist interfaces** with generous whitespace
- **Smooth, purposeful animations**
- **Interactive hover and click states**

### Visualization Types

| Type | Purpose | Interactive Features |
|------|---------|---------------------|
| Architecture Flow | Layer visualization | Hover details, animated data flow |
| Token Performance | Real-time metrics | Streaming visualization, waveforms |
| Memory Dashboard | Resource monitoring | Circular gauges, animated counters |
| Cache Analytics | Hit rate visualization | Stacked progress, color-coded levels |
| Patterns Network | Design patterns graph | Interactive node exploration |

---

## Design System

### Color Palette

```javascript
// Google Material Design Colors
const COLORS = {
  // Primary Colors
  googleBlue: '#4285F4',
  googleRed: '#EA4335',
  googleYellow: '#FBBC05',
  googleGreen: '#34A853',
  
  // Neutrals
  white: '#FFFFFF',
  nearBlack: '#202124',
  gray700: '#5F6368',
  gray500: '#9AA0A6',
  gray300: '#DADCE0',
  gray100: '#F8F9FA',
  
  // Accents
  lightBlue: '#E8F0FE',
  lightRed: '#FCE8E6',
  lightYellow: '#FEF7E0',
  lightGreen: '#E6F4EA'
};
```

### Typography Scale

```javascript
const TYPOGRAPHY = {
  headline: { size: 28, weight: '600', letterSpacing: -0.5 },
  title: { size: 20, weight: '500', letterSpacing: 0 },
  subtitle: { size: 16, weight: '500', letterSpacing: 0.15 },
  body: { size: 14, weight: '400', letterSpacing: 0.25 },
  caption: { size: 12, weight: '400', letterSpacing: 0.4 },
  label: { size: 11, weight: '500', letterSpacing: 0.5 }
};
```

### Elevation & Shadows

```javascript
const ELEVATION = {
  level1: { shadow: '0 1px 3px rgba(0,0,0,0.12)', blur: 3 },
  level2: { shadow: '0 4px 6px rgba(0,0,0,0.1)', blur: 6 },
  level3: { shadow: '0 10px 20px rgba(0,0,0,0.1)', blur: 12 },
  level4: { shadow: '0 15px 35px rgba(0,0,0,0.15)', blur: 20 }
};
```

### Animation Constants

```javascript
const ANIMATION = {
  duration: {
    fast: 150,
    normal: 300,
    slow: 500
  },
  easing: {
    standard: 'cubic-bezier(0.4, 0.0, 0.2, 1)',
    decelerate: 'cubic-bezier(0.0, 0.0, 0.2, 1)',
    accelerate: 'cubic-bezier(0.4, 0.0, 1, 1)'
  }
};
```

---

## Architecture Flow Visualization

### Clean Node-Based Architecture Diagram

```javascript
// ============================================================================
// Architecture Flow Visualization
// Google Studio-inspired layered architecture with animated data flow
// ============================================================================

// Design System Constants
const COLORS = {
  googleBlue: '#4285F4',
  googleRed: '#EA4335',
  googleYellow: '#FBBC05',
  googleGreen: '#34A853',
  white: '#FFFFFF',
  nearBlack: '#202124',
  gray700: '#5F6368',
  gray500: '#9AA0A6',
  gray300: '#DADCE0',
  gray100: '#F8F9FA',
  lightBlue: '#E8F0FE',
  lightGreen: '#E6F4EA'
};

// Architecture Layer Definition
const LAYERS = [
  {
    id: 'presentation',
    name: 'Presentation',
    y: 0,
    color: COLORS.googleBlue,
    lightColor: COLORS.lightBlue,
    components: [
      { name: 'ChatScreen', icon: '💬' },
      { name: 'Components', icon: '🧩' },
      { name: 'Theme', icon: '🎨' }
    ],
    description: 'UI layer with Jetpack Compose'
  },
  {
    id: 'domain',
    name: 'Domain',
    y: 0,
    color: COLORS.googleGreen,
    lightColor: COLORS.lightGreen,
    components: [
      { name: 'LlmEngine', icon: '🧠' },
      { name: 'ModelManager', icon: '📦' }
    ],
    description: 'Business logic and LLM operations'
  },
  {
    id: 'service',
    name: 'Service',
    y: 0,
    color: COLORS.googleYellow,
    lightColor: '#FEF7E0',
    components: [
      { name: 'Performance', icon: '⚡' },
      { name: 'Memory', icon: '💾' },
      { name: 'Cache', icon: '⚙️' }
    ],
    description: 'System services and optimization'
  },
  {
    id: 'data',
    name: 'Data',
    y: 0,
    color: COLORS.googleRed,
    lightColor: '#FCE8E6',
    components: [
      { name: 'Database', icon: '🗄️' },
      { name: 'DataStore', icon: '💿' },
      { name: 'FileSystem', icon: '📁' }
    ],
    description: 'Data persistence and storage'
  }
];

// State Management
let particles = [];
let connections = [];
let hoveredLayer = null;
let selectedLayer = null;
let animationTime = 0;
let canvasPadding = 40;

function setup() {
  const container = document.getElementById('architecture-viz') || document.body;
  const canvas = createCanvas(container.clientWidth || 900, 600);
  canvas.parent(container);
  
  textFont('Roboto, -apple-system, BlinkMacSystemFont, sans-serif');
  
  // Calculate layer positions
  const layerHeight = 100;
  const layerSpacing = 20;
  const startY = 120;
  
  LAYERS.forEach((layer, index) => {
    layer.y = startY + index * (layerHeight + layerSpacing);
    layer.height = layerHeight;
    layer.width = min(width - canvasPadding * 2, 800);
    layer.x = (width - layer.width) / 2;
  });
  
  // Initialize particles
  for (let i = 0; i < 15; i++) {
    spawnParticle();
  }
}

function windowResized() {
  const container = document.getElementById('architecture-viz') || document.body;
  resizeCanvas(container.clientWidth || 900, 600);
  
  // Recalculate positions
  LAYERS.forEach((layer) => {
    layer.width = min(width - canvasPadding * 2, 800);
    layer.x = (width - layer.width) / 2;
  });
}

function draw() {
  // Clean white background
  background(COLORS.white);
  
  animationTime += 0.016;
  
  // Draw title section
  drawTitle();
  
  // Draw connecting lines between layers
  drawConnections();
  
  // Draw layers
  LAYERS.forEach((layer, index) => {
    drawLayer(layer, index);
  });
  
  // Draw animated data flow particles
  updateAndDrawParticles();
  
  // Draw info panel for hovered/selected layer
  drawInfoPanel();
  
  // Draw legend
  drawLegend();
}

function drawTitle() {
  // Main title
  fill(COLORS.nearBlack);
  noStroke();
  textAlign(LEFT);
  textSize(24);
  textStyle(BOLD);
  text('System Architecture', canvasPadding, 50);
  
  // Subtitle
  fill(COLORS.gray700);
  textSize(14);
  textStyle(NORMAL);
  text('Layered architecture with reactive data flow', canvasPadding, 75);
  
  // Decorative line
  stroke(COLORS.googleBlue);
  strokeWeight(3);
function drawGauge(title, value, min, max, x, y, color) {
  let size = 120;
  
  // Gauge background
  fill(50);
  noStroke();
  circle(x, y, size);
  
  // Gauge arc
  let angle = map(value, min, max, -PI, 0);
  stroke(color[0], color[1], color[2]);
  strokeWeight(8);
  noFill();
  arc(x, y, size - 10, size - 10, -PI, angle);
  
  // Value text
  fill(255);
  textSize(20);
  textAlign(CENTER);
  text(value.toFixed(1), x, y + 10);
  
  // Title
  textSize(12);
  text(title, x, y + size/2 + 20);
}

function drawStatusIndicators() {
  let indicators = [
    { label: "Engine", status: "READY", color: [100, 255, 100] },
    { label: "Memory", status: "NORMAL", color: [100, 255, 100] },
    { label: "Cache", status: "ACTIVE", color: [100, 255, 100] },
    { label: "Thermal", status: "NORMAL", color: [100, 255, 100] }
  ];
  
  let startX = 500;
  let startY = 500;
  
  for (let i = 0; i < indicators.length; i++) {
    let ind = indicators[i];
    let x = startX + i * 100;
    
    // Status circle
    fill(ind.color[0], ind.color[1], ind.color[2]);
    noStroke();
    circle(x, startY, 20);
    
    // Label
    fill(255);
    textSize(12);
    textAlign(CENTER);
    text(ind.label, x, startY + 30);
    
    // Status text
    textSize(10);
    text(ind.status, x, startY + 45);
  }
}
```

---

## Usage Instructions

### Running Visualizations

1. **Online Editor**: Use [p5.js Web Editor](https://editor.p5js.org/)
   - Create a new sketch
   - Copy the visualization code
   - Click Play

2. **Local Setup**:
   ```bash
   # Install p5.js
   npm install p5
   
   # Create HTML file
   cat > index.html << 'EOF'
   <!DOCTYPE html>
   <html>
   <head>
     <script src="https://cdnjs.cloudflare.com/ajax/libs/p5.js/1.9.0/p5.min.js"></script>
     <script src="architecture-viz.js"></script>
   </head>
   <body style="margin:0; background:#1e1e1e;"></body>
   </html>
   EOF
   ```

3. **Embedding**: Use iframe in documentation
   ```html
   <iframe src="visualization.html" width="800" height="600"></iframe>
   ```

---

## Related Documents

| Document | Relationship | Description |
|----------|--------------|-------------|
| [System Diagrams](diagrams.md) | Static | Mermaid diagrams |
| [Architecture Overview](../architecture/overview.md) | Context | High-level architecture |
| [Dynamic Analysis](../analysis/dynamic-analysis.md) | Data Source | Runtime behavior |

---

*Document maintained by the Technical Architecture Team*  
*Last updated: 2026-02-01*  
*Classification: Educational*
