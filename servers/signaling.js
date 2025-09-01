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
                    if (clients[data.target] && clients[data.target].readyState === WebSocket.OPEN) {
                        console.log(`Sending offer from ${data.initiatorId || data.userId} to ${data.target}`);
                        try {
                            clients[data.target].send(JSON.stringify(data));
                        } catch (error) {
                            console.error(`Error sending offer to ${data.target}:`, error.message);
                            delete clients[data.target];
                        }
                    } else {
                        console.log(`Target ${data.target} not found or connection closed.`);
                        if (clients[data.target]) delete clients[data.target];
                    }
                    break;

                case "answer":
                    if (clients[data.target] && clients[data.target].readyState === WebSocket.OPEN) {
                        console.log(`Sending answer from ${data.initiatorId || data.userId || 'unknown'} to ${data.target}`);
                        try {
                            clients[data.target].send(JSON.stringify(data));
                        } catch (error) {
                            console.error(`Error sending answer to ${data.target}:`, error.message);
                            delete clients[data.target];
                        }
                    } else {
                        console.log(`Target ${data.target} not found or connection closed.`);
                        if (clients[data.target]) delete clients[data.target];
                    }
                    break;

                case "candidate":
                    if (clients[data.target] && clients[data.target].readyState === WebSocket.OPEN) {
                        console.log(`Relaying ICE candidate from ${data.userId || 'unknown'} to ${data.target}`);
                        try {
                            clients[data.target].send(JSON.stringify(data));
                        } catch (error) {
                            console.error(`Error sending ICE candidate to ${data.target}:`, error.message);
                            delete clients[data.target];
                        }
                    } else {
                        console.log(`Target ${data.target} not found or connection closed.`);
                        if (clients[data.target]) delete clients[data.target];
                    }
                    break;

                case "call_request":
                    const receiverId = data.receiver_id;
                    if (clients[receiverId] && clients[receiverId].readyState === WebSocket.OPEN) {
                        console.log(`Relaying call request from ${data.caller_id} to ${receiverId}`);
                        try {
                            clients[receiverId].send(JSON.stringify(data));
                        } catch (error) {
                            console.error(`Error sending call request to ${receiverId}:`, error.message);
                            delete clients[receiverId];
                        }
                    } else {
                        console.log(`Receiver ${receiverId} not found or connection closed.`);
                        if (clients[receiverId]) delete clients[receiverId];
                    }
                    break;

                case "call_accepted":
                    const callerId = data.caller_id;
                    if (clients[callerId] && clients[callerId].readyState === WebSocket.OPEN) {
                        console.log(`Relaying call acceptance from ${data.receiver_id} to ${callerId}`);
                        try {
                            clients[callerId].send(JSON.stringify(data));
                        } catch (error) {
                            console.error(`Error sending call acceptance to ${callerId}:`, error.message);
                            delete clients[callerId];
                        }
                    } else {
                        console.log(`Caller ${callerId} not found or connection closed.`);
                        if (clients[callerId]) delete clients[callerId];
                    }
                    break;

                case "call_declined":
                    const originalCaller = data.caller_id;
                    if (clients[originalCaller] && clients[originalCaller].readyState === WebSocket.OPEN) {
                        console.log(`Relaying call decline from ${data.receiver_id} to ${originalCaller}`);
                        try {
                            clients[originalCaller].send(JSON.stringify(data));
                        } catch (error) {
                            console.error(`Error sending call decline to ${originalCaller}:`, error.message);
                            delete clients[originalCaller];
                        }
                    } else {
                        console.log(`Caller ${originalCaller} not found or connection closed.`);
                        if (clients[originalCaller]) delete clients[originalCaller];
                    }
                    break;

                case "call_ended":
                    const endTarget = data.target;
                    if (clients[endTarget] && clients[endTarget].readyState === WebSocket.OPEN) {
                        console.log(`Notifying ${endTarget} that call with ${data.userId} ended`);
                        try {
                            clients[endTarget].send(JSON.stringify({
                                type: "call_ended",
                                userId: data.userId
                            }));
                        } catch (error) {
                            console.error(`Error notifying call end to ${endTarget}:`, error.message);
                            delete clients[endTarget];
                        }
                    } else {
                        console.log(`Target ${endTarget} not found or connection closed for call end notification.`);
                        if (clients[endTarget]) delete clients[endTarget];
                    }
                    console.log(`Call ended between ${data.userId} and ${endTarget} - cleaning up any call state`);
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
