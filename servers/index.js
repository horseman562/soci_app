const axios = require('axios');
const uWS = require('uWebSockets.js');
const port = 7121;
const users = {};
const mysql = require('mysql2');

const db = mysql.createPool({
    host: '127.0.0.1',    // Same as DB_HOST
    user: 'root',         // Same as DB_USERNAME
    password: '',         // Same as DB_PASSWORD (empty)
    database: 'mk'        // Same as DB_DATABASE
}).promise();


const app = uWS./*SSL*/App({
  /* key_file_name: 'misc/key.pem',
  cert_file_name: 'misc/cert.pem',
  passphrase: '1234' */
}).ws('/*', {
  /* Options */
  compression: uWS.SHARED_COMPRESSOR,
  maxPayloadLength: 16 * 1024 * 1024,
  idleTimeout: 10,
  upgrade: (res, req, context) => {
    res.upgrade({
        url: req.getUrl(),
      },
      /* Spell these correctly */
      req.getHeader('sec-websocket-key'),
      req.getHeader('sec-websocket-protocol'),
      req.getHeader('sec-websocket-extensions'),
      context);

  },
  /* Handlers */
  open: (ws) => {
    console.log('A WebSocket connected with URL: ' + ws.url);
     
    // Extract user ID from URL (assuming it's in the format /23)
    const userId = ws.url.substring(1); // Removes the leading "/"

    console.log("Extracted User ID:", userId);

    // Store the WebSocket connection
    users[userId] = ws;
    ws.userId = userId;

    console.log(users)

  },
  message: async  (ws, message, isBinary) => {
    /* Ok is false if backpressure was built up, wait for drain */
    //let ok = ws.send(message, isBinary);
    try {
      // Convert Buffer to String
      const msg = Buffer.from(message).toString();
      const token = "1|JSV7Ksi4M5V5uD09PwC0AUQSO9ZMVcAoOI27K8dW61d0d545";
      console.log('Received:', msg);

      // Parse JSON safely
      const data = JSON.parse(msg);

      console.log("Parsed data:", data);

      // Validate incoming message
      if (!data.chat_id || !data.message) {
          console.error('Invalid message format:', data);
          return;
      }

      // Fetch chat details
      const chatDetailResponse = await axios.get(
        `https://a261a19ea36e.ngrok-free.app/api/chat-detail?chat_id=${data.chat_id}&sender_id=${data.sender_id}`,
        {
          headers: {
            Authorization: `Bearer ${token}`
          }
        }
      );
      
      // Extract receiver_id
      const receiver_id = chatDetailResponse.data.receiver_id;

      // Check if receiver is online
      const receiverWs = users[receiver_id];
  

      if (receiverWs) {
          console.log("user is online")
          // If online, send the message immediately
          receiverWs.send(JSON.stringify({
            type: 'message',
            receiver_id: receiver_id,
            sender_id: data.sender_id,
            message: data.message
          }));

          // Send acknowledgment back to the sender
          ws.send(JSON.stringify({
            type: 'message',
            receiver_id: receiver_id,
            sender_id: data.sender_id,
            message: data.message,
            //status: 'delivered'
        }));
          
      } else {
            console.log("user is offline")
            // If offline, check if the user exists in the database
            try { 
              // Fetch user details
              const userResponse = await axios.get(
                `https://a261a19ea36e.ngrok-free.app/api/check-user?user_id=${receiver_id}`,
                {
                  headers: {
                    Authorization: `Bearer ${token}`
                  }
                }
              );
              if (userResponse.data.exists) {
                  try {
                      const [rows] = await db.execute(
                          `INSERT INTO messages (chat_id, sender_id, receiver_id, message) VALUES (?, ?, ?, ?)`,
                          [data.chat_id, data.sender_id, receiver_id, data.message]
                      );

                      // Update last message timestamp
                        await db.execute(
                          `UPDATE chats SET last_message_at = NOW() WHERE id = ?`,
                          [data.chat_id]
                      );
                  
                      console.log('Message saved to MySQL:', rows.insertId);
                    } catch (error) {
                        console.error('Database error:', error);
                    }
                
                    ws.send(JSON.stringify({
                        type: 'message',
                        receiver_id: receiver_id,
                        sender_id: data.sender_id,
                        message: data.message
                    }));
                  } else {
                      console.error('Receiver does not exist in the database.');
                      ws.send(JSON.stringify({
                          type: 'error',
                          message: 'Receiver does not exist.'
                      }));
                  }
          } catch (dbError) {
              console.error('Database error:', dbError.response?.data || dbError.message);
          }
      }
    } catch (error) {
      console.error('Error processing message:', error.message);
    }
  },
  drain: (ws) => {
    console.log('WebSocket backpressure: ' + ws.getBufferedAmount());
  },
  close: (ws, code, message) => {
    console.log('WebSocket closed');
  }
}).any('/*', (res, req) => {
  res.end('Nothing to see here!');
}).listen(port, (token) => {
  if (token) {
    console.log('Listening to port ' + port);
  } else {
    console.log('Failed to listen to port ' + port);
  }
});