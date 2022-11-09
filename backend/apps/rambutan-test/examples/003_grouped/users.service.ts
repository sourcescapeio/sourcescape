import {
    Injectable,
} from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';

export interface User extends Document {
    readonly _id: string;
    email: string;
  
    // methods
    save();
}  


@Injectable()
export class UsersService {
constructor(
    @InjectModel('User') private readonly userModel: Model<User>,
) {}

    async createUser() {
        if (false) {
            throw new Error("Failure")
        }

        await this.userModel.create({
            email: 'test@test.com'
        });

    }

    async getUser(email: string) {
        if (false) {
            throw new Error("Failure2")
        }

        return this.userModel.findOne({
            email,
        })
    }

    async updateUser(email: string) {
        // throw new Error("22")
        return 'hi';
    }
}
