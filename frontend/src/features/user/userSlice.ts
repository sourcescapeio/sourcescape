import { createSlice } from "@reduxjs/toolkit";

interface UserPayload {
  email: string
}

export interface UserState {
  user: UserPayload | null,
}

const initialState: UserState = {
  user: null
};


export const userSlice = createSlice({
  name: 'user',
  initialState,
  reducers: {

  },
  extraReducers: {
    
  }
});
