// Import the functions you need from the SDKs you need
import { initializeApp } from "firebase/app";
import { getAnalytics } from "firebase/analytics";
// TODO: Add SDKs for Firebase products that you want to use
// https://firebase.google.com/docs/web/setup#available-libraries

// Your web app's Firebase configuration
// For Firebase JS SDK v7.20.0 and later, measurementId is optional
const firebaseConfig = {
  apiKey: "AIzaSyCRPqCOtrfKhXqUzYTT6Ku0emSbm1E7stw",
  authDomain: "miniproject-7893.firebaseapp.com",
  databaseURL: "https://miniproject-7893-default-rtdb.firebaseio.com",
  projectId: "miniproject-7893",
  storageBucket: "miniproject-7893.firebasestorage.app",
  messagingSenderId: "937721189695",
  appId: "1:937721189695:web:d816720d8fea2bb86cef8a",
  measurementId: "G-D6GPT5F2VN"
};

// Initialize Firebase
const app = initializeApp(firebaseConfig);
const analytics = getAnalytics(app);