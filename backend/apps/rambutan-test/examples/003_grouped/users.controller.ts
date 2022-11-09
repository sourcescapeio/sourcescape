import {
    Controller,
    Get,
    Param,
    Post,
    Put,
  } from '@nestjs/common';
  import { UsersService } from './users.service';
  
  @Controller()
  export class UsersController {
    constructor(
      private readonly usersService: UsersService,
    ) {}

    @Post('/users')
    async createUser() {
        await this.usersService.createUser()

        return {
            status: 'ok'
        };
    }

    @Get('/users/:email')
    async getUser(@Param('email') email: string) {
        const user = await this.usersService.getUser(email);

        return {
            user,
        };
    } 


    @Put('/users/:email')
    async updateUser(@Param('email') email: string) {
        const user = await this.usersService.updateUser(email);

        return {
            user,
        };
    }
}
