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
    console.log('A WebSocket connected ' + ws.myData);
    let url = ws.url.replace(/^\//, '');
    let urls = url.split("/");

    console.log(urls)

    // akan guna nanti //
    /* console.log('Client connected');

        const userId = req.getQuery(); // user_id=1
        users[userId] = ws;
        ws.userId = userId;

        ws.send(JSON.stringify({ type: 'welcome', message: 'Connected to WebSocket!' })); */
    //

  },
  message: async  (ws, message, isBinary) => {
    /* Ok is false if backpressure was built up, wait for drain */
    //let ok = ws.send(message, isBinary);
    try {
      // Convert Buffer to String
      const msg = Buffer.from(message).toString();
      console.log('Received:', msg);

      // Parse JSON safely
      const data = JSON.parse(msg);

      console.log("Parsed data:", data);

      // Validate incoming message
      if (!data.chat_id || !data.message) {
          console.error('Invalid message format:', data);
          return;
      }

      // Check if receiver is online
      const receiverWs = users[data.receiver_id];

      if (receiverWs) {
          // If online, send the message immediately
          receiverWs.send(JSON.stringify({
              type: 'message',
              sender: ws.userId,
              message: data.message
          }));
      } else {
            // If offline, check if the user exists in the database
            try { 
              const token = "30|XYRSqdwtTuYt5jzN3ihDUY0EiCiqBgtfUd4Tjt1V3d548cad";

              // Fetch chat details
              const chatDetailResponse = await axios.get(
                `http://2f81-42-153-134-3.ngrok-free.app/api/chat-detail?chat_id=${data.chat_id}&sender_id=${data.sender_id}`,
                {
                  headers: {
                    Authorization: `Bearer ${token}`
                  }
                }
              );
              
              // Extract receiver_id
              const receiver_id = chatDetailResponse.data.receiver_id;
              
              // Fetch user details
              const userResponse = await axios.get(
                `http://2f81-42-153-134-3.ngrok-free.app/api/check-user?user_id=${receiver_id}`,
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