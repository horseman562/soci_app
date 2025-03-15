const WebSocket = require("ws");
const server = new WebSocket.Server({ port: 3000 });

let clients = {};

server.on("connection", (ws) => {
    ws.on("message", (message) => {
        const data = JSON.parse(message);

        switch (data.type) {
            case "register":
                clients[data.userId] = ws;
                console.log(`User ${data.userId} registered.`);
                break;

            case "offer":
            case "answer":
            case "candidate":
                if (clients[data.target]) {
                    clients[data.target].send(JSON.stringify(data));
                }
                break;
        }
    });

    ws.on("close", () => {
        for (let user in clients) {
            if (clients[user] === ws) {
                delete clients[user];
                console.log(`User ${user} disconnected.`);
            }
        }
    });
});

console.log("WebRTC Signaling Server running on ws://localhost:3000");
