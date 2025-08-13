const WebSocket = require("ws");
const server = new WebSocket.Server({ port: 3001 });

let clients = {};

server.on("connection", (ws) => {
    console.log("New client connected.");

    ws.on("message", (message) => {
        try {
            const data = JSON.parse(message);
            console.log("Received message:", data);

            switch (data.type) {
                case "register":
                    clients[data.userId] = ws;
                    console.log(`User ${data.userId} registered.`);
                    break;

                case "offer":
                    if (clients[data.target]) {
                        console.log(`Sending offer from ${data.initiatorId || data.userId} to ${data.target}`);
                        clients[data.target].send(JSON.stringify(data));
                    } else {
                        console.log(`Target ${data.target} not found.`);
                    }
                    break;

                case "answer":
                    if (clients[data.target]) {
                        console.log(`Sending answer from ${data.initiatorId || data.userId || 'unknown'} to ${data.target}`);
                        clients[data.target].send(JSON.stringify(data));
                    } else {
                        console.log(`Target ${data.target} not found.`);
                    }
                    break;

                case "candidate":
                    if (clients[data.target]) {
                        console.log(`Relaying ICE candidate from ${data.userId || 'unknown'} to ${data.target}`);
                        clients[data.target].send(JSON.stringify(data));
                    } else {
                        console.log(`Target ${data.target} not found.`);
                    }
                    break;

                default:
                    console.log("Unknown message type:", data.type);
            }
        } catch (error) {
            console.error("Error processing message:", error.message);
        }
    });

    ws.on("close", () => {
        let disconnectedUser = null;
        for (let userId in clients) {
            if (clients[userId] === ws) {
                disconnectedUser = userId;
                delete clients[userId];
                console.log(`User ${userId} disconnected.`);
                break;
            }
        }
    });

    ws.on("error", (error) => {
        console.error("WebSocket error:", error.message);
    });
});

console.log("WebRTC Signaling Server running on ws://localhost:3001");
